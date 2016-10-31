package com.v1ct04.benchstack.driver;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Benchmark {

    private static final Logger LOGGER = Logger.getLogger(Benchmark.class.getName());

    private static final long DELAY_LIMIT_MILLIS = 1000;
    private static final double PERCENTILE_THRESHOLD = 0.95;
    private static final int COMPLIANCE_TEST_SAMPLES = 3;

    private static final int EXP_STEP_WAIT_TIME_SEC = 15;
    private static final int EXP_STEP_MULTIPLIER = 5;
    private static final int EXP_STEP_INITIAL_WORKERS = 10;

    private static final int BIN_SEARCH_WAIT_TIME_SEC = 30;
    private static final int BIN_SEARCH_THRESHOLD = 5;

    private static final int FINE_TUNE_INITIAL_STEP = 10;
    private static final int FINE_TUNE_WAIT_TIME_SEC = 30;

    private static final int STABLE_STATS_WAIT_TIME_MIN = 60;

    private Thread mBenchmarkThread;
    private ConcurrentWorkersPool mWorkersPool;
    private SettableFuture<Statistics> mResult;

    private final Runnable mFunction;

    private final PercentileCalculator mPercentileCalculator = new PercentileCalculator(DELAY_LIMIT_MILLIS);
    private volatile Statistics.Calculator mStatsCalculator;

    public Benchmark(Runnable function) {
        mFunction = function;
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
            execExponentialLoadStep();
            LOGGER.fine("Finished exponential step. Workers: " + mWorkersPool.getWorkerCount());
            execBinarySearchStep();
            LOGGER.fine("Finished binary search step. Workers: " + mWorkersPool.getWorkerCount());
            execFineTuneStep();
            LOGGER.fine("Finished fine tuning. Workers: " + mWorkersPool.getWorkerCount());
            mResult.set(execCalculateStatsStep());
            LOGGER.info("Finished Benchmark.");
        } catch (InterruptedException e) {
            LOGGER.warning("Benchmark interrupted.");
            mResult.setException(e);
        } catch (Throwable t) {
            LOGGER.severe("Benchmark failed with exception: " + t);
            mResult.setException(t);
            Throwables.propagate(t);
        } finally {
            mWorkersPool.shutdown();
            mWorkersPool = null;
            mStatsCalculator = null;
        }
    }

    private void execExponentialLoadStep() throws InterruptedException {
        setWorkerCount(EXP_STEP_INITIAL_WORKERS);
        LOGGER.finer("Starting exponential step.");
        while (isComplying(EXP_STEP_WAIT_TIME_SEC, TimeUnit.SECONDS)) {
            setWorkerCount(EXP_STEP_MULTIPLIER * mWorkersPool.getWorkerCount());
        }
    }

    private void execBinarySearchStep() throws InterruptedException {
        int max = mWorkersPool.getWorkerCount();
        int min = (max == EXP_STEP_INITIAL_WORKERS ? 1 : max / EXP_STEP_MULTIPLIER);

        LOGGER.finer("Starting binary search step.");
        while (max - min > BIN_SEARCH_THRESHOLD) {
            setWorkerCount((min + max) / 2);

            if (isComplying(BIN_SEARCH_WAIT_TIME_SEC, TimeUnit.SECONDS)) {
                LOGGER.finer("Adjusting minimum search bound.");
                min = mWorkersPool.getWorkerCount();
            } else {
                LOGGER.finer("Adjusting maximum search bound.");
                max = mWorkersPool.getWorkerCount();
            }
        }
        setWorkerCount(min);
    }

    private void execFineTuneStep() throws InterruptedException {
        int workingCount;
        for (int step = FINE_TUNE_INITIAL_STEP; step > 0; step /= 2) {
            LOGGER.finer("Fine tuning with step: " + step);
            do {
                workingCount = mWorkersPool.getWorkerCount();
                setWorkerCount(workingCount + step);
            } while (isComplying(FINE_TUNE_WAIT_TIME_SEC, TimeUnit.SECONDS));
            setWorkerCount(workingCount);
        }
    }

    private Statistics execCalculateStatsStep() throws InterruptedException {
        LOGGER.finer("Calculating stable statistics for worker count: " + mWorkersPool.getWorkerCount());

        mStatsCalculator = Statistics.calculator();
        TimeUnit.MINUTES.sleep(STABLE_STATS_WAIT_TIME_MIN);
        return mStatsCalculator.calculate();
    }

    private void setWorkerCount(int count) {
        LOGGER.finer("Setting worker count to: " + count);
        mWorkersPool.setWorkerCount(count);
        mPercentileCalculator.reset();
    }

    private boolean isComplying(int baseWaitTime, TimeUnit unit) throws InterruptedException {
        long waitTime = baseWaitTime;

        List<Double> percentiles = Lists.newArrayList();
        do {
            LOGGER.finest("Compliance check, waiting: " + waitTime + " " + unit);
            unit.sleep(waitTime);

            double percentile = mPercentileCalculator.getCurrentPercentile();
            LOGGER.finest("Current percentile: " + percentile);
            percentiles.add(percentile);
            if (percentiles.size() < COMPLIANCE_TEST_SAMPLES) continue;

            boolean complies = percentiles.stream().allMatch(p -> p > PERCENTILE_THRESHOLD);
            boolean increasing = isIncreasing(percentiles.stream());
            boolean decreasing = isIncreasing(percentiles.stream().map(x -> -x));
            LOGGER.finest(() -> String.format(
                    "Collected %d percentiles. {complies=%b, increasing=%b, decreasing=%b}",
                    percentiles.size(), complies, increasing, decreasing));

            if (complies && increasing) {
                return true;
            } else if (!complies && decreasing) {
                return false;
            } else if (waitTime == baseWaitTime) {
                double last = percentiles.get(percentiles.size() - 1);
                percentiles.clear();
                percentiles.add(last);
                waitTime *= COMPLIANCE_TEST_SAMPLES;
            } else {
                break;
            }
        } while (true);

        double percentile = mPercentileCalculator.getCurrentPercentile();
        LOGGER.finest("Final compliance check instant percentile: " + percentile);
        return percentile > PERCENTILE_THRESHOLD;
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
