package com.v1ct04.benchstack.driver;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.BinarySearchStepConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.ExponentialStepConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.FineTuneStepConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.StableStatsStepConfig;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Benchmark {

    private static final Logger LOGGER = Logger.getLogger(Benchmark.class.getName());

    private final BenchmarkConfig mConfig;
    private final Runnable mFunction;
    private final PercentileCalculator mPercentileCalculator;

    private Thread mBenchmarkThread;
    private ConcurrentWorkersPool mWorkersPool;
    private SettableFuture<Statistics> mResult;

    private volatile Statistics.Calculator mStatsCalculator;

    public Benchmark(BenchmarkConfig config, Runnable function) {
        mConfig = config;
        mFunction = function;
        mPercentileCalculator = new PercentileCalculator(mConfig.getDelayLimitMillis());
    }

    private void workerFunction() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        mFunction.run();
        long elapsedTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);

        mPercentileCalculator.appendValue(elapsedTime);
        if (mStatsCalculator != null) {
            mStatsCalculator.appendValue(elapsedTime / 1000.0);
        }
    }

    public ListenableFuture<Statistics> start() {
        if (mResult != null) {
            throw new IllegalStateException("Benchmark already started");
        }

        mResult = SettableFuture.create();
        mBenchmarkThread = Executors.defaultThreadFactory().newThread(this::executeBenchmark);
        mBenchmarkThread.start();
        return mResult;
    }

    public void stop() {
        if (mBenchmarkThread == null) {
            throw new IllegalStateException("Must have started benchmark to call stop");
        }
        mBenchmarkThread.interrupt();
    }

    private void executeBenchmark() {
        mWorkersPool = new ConcurrentWorkersPool(this::workerFunction);
        try {
            LOGGER.info("Starting Benchmark.");
            Range<Integer> searchLimits = execExponentialLoadStep(mConfig.getExponentialStepConfig());
            LOGGER.fine("Finished exponential step. Workers: " + mWorkersPool.getWorkerCount());

            execBinarySearchStep(mConfig.getBinarySearchConfig(), searchLimits);
            LOGGER.fine("Finished binary search step. Workers: " + mWorkersPool.getWorkerCount());

            execFineTuneStep(mConfig.getFineTuneConfig());
            LOGGER.fine("Finished fine tuning. Workers: " + mWorkersPool.getWorkerCount());

            Statistics result = execCalculateStatsStep(mConfig.getStableStatsConfig());
            mResult.set(result);
            LOGGER.info("Finished Benchmark.");
        } catch (InterruptedException e) {
            mResult.setException(e);
            LOGGER.warning("Benchmark interrupted.");
        } catch (Throwable t) {
            mResult.setException(t);
            LOGGER.severe("Benchmark failed with exception: " + t);
            Throwables.propagate(t);
        } finally {
            mWorkersPool.shutdown();
            mWorkersPool = null;
            mStatsCalculator = null;
        }
    }

    private Range<Integer> execExponentialLoadStep(ExponentialStepConfig config) throws InterruptedException {
        int lastWorkerCount = 1;
        setWorkerCount(config.getInitialWorkers());

        LOGGER.finer("Starting exponential step.");
        while (isComplying(config)) {
            lastWorkerCount = mWorkersPool.getWorkerCount();
            setWorkerCount(config.getMultiplier() * lastWorkerCount);
        }
        return Range.closed(lastWorkerCount, mWorkersPool.getWorkerCount());
    }

    private void execBinarySearchStep(BinarySearchStepConfig config, Range<Integer> limits) throws InterruptedException {
        int min = limits.lowerEndpoint(), max = limits.upperEndpoint();

        LOGGER.finer("Starting binary search step.");
        while (max - min > config.getThreshold()) {
            setWorkerCount((min + max) / 2);

            if (isComplying(config)) {
                LOGGER.finer("Adjusting minimum search bound.");
                min = mWorkersPool.getWorkerCount();
            } else {
                LOGGER.finer("Adjusting maximum search bound.");
                max = mWorkersPool.getWorkerCount();
            }
        }
        setWorkerCount(min);
    }

    private void execFineTuneStep(FineTuneStepConfig config) throws InterruptedException {
        for (int step = config.getInitialStep(); step > 0; step /= 2) {
            LOGGER.finer("Fine tuning with step: " + step);
            int workingCount;
            do {
                workingCount = mWorkersPool.getWorkerCount();
                setWorkerCount(workingCount + step);
            } while (isComplying(config));
            setWorkerCount(workingCount);
        }
    }

    private Statistics execCalculateStatsStep(StableStatsStepConfig config) throws InterruptedException {
        LOGGER.finer("Calculating stable statistics for worker count: " + mWorkersPool.getWorkerCount());

        mStatsCalculator = Statistics.calculator();
        TimeUnit.MINUTES.sleep(config.getWaitTimeMin());
        return mStatsCalculator.calculate();
    }

    private void setWorkerCount(int count) {
        LOGGER.finer("Setting worker count to: " + count);
        mWorkersPool.setWorkerCount(count);
        mPercentileCalculator.reset();
    }

    private boolean isComplying(Message msg) throws InterruptedException {
        FieldDescriptor waitTimeField = msg.getDescriptorForType().findFieldByName("baseWaitTimeSec");
        return isComplying((Long) msg.getField(waitTimeField), TimeUnit.SECONDS);
    }

    private boolean isComplying(long baseWaitTime, TimeUnit unit) throws InterruptedException {
        long waitTime = baseWaitTime;
        int complianceTestSamples = mConfig.getComplianceTestSamples();
        double percentileThreshold = mConfig.getPercentileThreshold();

        List<Double> percentiles = Lists.newArrayList();
        do {
            LOGGER.finest("Compliance check, waiting: " + waitTime + " " + unit);
            unit.sleep(waitTime);

            double percentile = mPercentileCalculator.getCurrentPercentile();
            LOGGER.finest("Current percentile: " + percentile);
            percentiles.add(percentile);
            if (percentiles.size() < complianceTestSamples) continue;

            boolean complies = percentiles.stream().allMatch(p -> p > percentileThreshold);
            boolean increasing = isIncreasing(percentiles.stream());
            boolean decreasing = isIncreasing(percentiles.stream().map(x -> -x));
            LOGGER.finest(() -> String.format(
                    "Collected %d percentiles. {complies=%b, increasing=%b, decreasing=%b}",
                    complianceTestSamples, complies, increasing, decreasing));

            if (complies && increasing) {
                return true;
            } else if (!complies && decreasing) {
                return false;
            } else if (waitTime == baseWaitTime) {
                double last = percentiles.get(percentiles.size() - 1);
                percentiles.clear();
                percentiles.add(last);
                waitTime *= complianceTestSamples;
            } else {
                break;
            }
        } while (true);

        double percentile = mPercentileCalculator.getCurrentPercentile();
        LOGGER.finest("Final compliance check instant percentile: " + percentile);
        return percentile > percentileThreshold;
    }

    private static <Type extends Comparable<Type>> boolean isIncreasing(Stream<Type> val) {
        Iterator<Type> it = val.iterator();
        if (!it.hasNext()) return true;

        Type last = it.next();
        while (it.hasNext()) {
            Type curr = it.next();
            if (curr.compareTo(last) < 0) {
                return false;
            }
            last = curr;
        }
        return true;
    }
}
