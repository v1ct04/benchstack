package com.v1ct04.benchstack.driver;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.v1ct04.benchstack.concurrent.MoreFutures;
import com.v1ct04.benchstack.concurrent.TimeCondition;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.BinarySearchStepConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.ExponentialStepConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.FineTuneStepConfig;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig.StableStatsStepConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.*;

public class Benchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(Benchmark.class);

    private final BenchmarkConfig mConfig;
    private final BenchmarkAction mAction;
    private final PercentileCalculator mPercentileCalculator;

    private Thread mBenchmarkThread;
    private ConcurrentWorkersPool mWorkersPool;
    private SettableFuture<Statistics> mResult;

    private volatile Statistics.Calculator mStatsCalculator;

    public Benchmark(BenchmarkConfig config, BenchmarkAction action) {
        mConfig = config;
        mAction = action;
        mPercentileCalculator = new PercentileCalculator(mConfig.getDelayLimitMillis());
    }

    private void workerFunction(int workerNum) {
        mPercentileCalculator.startExecution();
        try {
            long nanoStartTime = System.nanoTime();
            Throwable t = null;
            try {
                MoreFutures.onlyGet(mAction.execute(workerNum));
            } catch (InterruptedException | CancellationException ex) {
                return;
            } catch (Exception e) {
                t = e instanceof ExecutionException ? e.getCause() : e;
                LOGGER.warn("Worker {}: Action threw exception: {}", workerNum, t.toString());
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoStartTime);

            if (t == null || elapsedMillis > mConfig.getDelayLimitMillis()) {
                mPercentileCalculator.appendValue(elapsedMillis);
                Statistics.Calculator calculator = mStatsCalculator;
                if (calculator!= null) {
                    calculator.appendValue(elapsedMillis / 1000.0);
                }
            }
        } finally {
            mPercentileCalculator.finishExecution();
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
            logInfoAndStdOut("Starting Benchmark.");

            Range<Integer> searchLimits = execExponentialLoadStep(mConfig.getExponentialStepConfig());

            execBinarySearchStep(mConfig.getBinarySearchConfig(), searchLimits);

            execFineTuneStep(mConfig.getFineTuneConfig());

            Statistics result = execCalculateStatsStep(mConfig.getStableStatsConfig());
            mResult.set(result);

            logInfoAndStdOut("Finished Benchmark.");
        } catch (InterruptedException e) {
            mResult.setException(e);
            LOGGER.warn("Benchmark interrupted.");
        } catch (Throwable t) {
            mResult.setException(t);
            LOGGER.error("Benchmark failed with exception: {}", t);
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

        logInfoAndStdOut("Starting exponential step.");
        while (isComplying(config)) {
            lastWorkerCount = mWorkersPool.getWorkerCount();
            setWorkerCount(config.getMultiplier() * lastWorkerCount);
        }
        logInfoAndStdOut("Finished exponential step. Workers: %d", mWorkersPool.getWorkerCount());

        return Range.closed(lastWorkerCount, mWorkersPool.getWorkerCount());
    }

    private void execBinarySearchStep(BinarySearchStepConfig config, Range<Integer> limits) throws InterruptedException {
        int min = limits.lowerEndpoint(), max = limits.upperEndpoint();
        logInfoAndStdOut("Starting binary search step between %d and %d", min, max);

        int threshold = config.getThreshold();
        while (max - min > threshold) {
            double currentOpsPerSec = mWorkersPool.getCurrentOperationsPerSec();
            if (currentOpsPerSec > min + threshold && currentOpsPerSec < max - threshold) {
                setWorkerCount((int) currentOpsPerSec);
            } else {
                setWorkerCount((min + max) / 2);
            }

            if (isComplying(config)) {
                LOGGER.trace("Adjusting minimum search bound.");
                min = mWorkersPool.getWorkerCount();
            } else {
                LOGGER.trace("Adjusting maximum search bound.");
                max = mWorkersPool.getWorkerCount();
            }
        }
        setWorkerCount(min);
        logInfoAndStdOut("Finished binary search step. Final workers: %d", min);
    }

    private void execFineTuneStep(FineTuneStepConfig config) throws InterruptedException {
        logInfoAndStdOut("Starting fine tune step.");

        int step = 2 * config.getInitialStep();
        while (!isComplying(config)) {
            LOGGER.debug("Fine tuning down with double step: {}", step);
            setWorkerCount(Math.max(mWorkersPool.getWorkerCount() - step, 0));
        }

        while (step > 1) {
            step /= 2;
            int complyingCount;
            do {
                complyingCount = mWorkersPool.getWorkerCount();
                LOGGER.debug("Fine tuning up with step: {}", step);
                setWorkerCount(complyingCount + step);
            } while (isComplying(config));

            setWorkerCount(complyingCount);
        }
        logInfoAndStdOut("Finished fine tuning. Workers: %d", mWorkersPool.getWorkerCount());
    }

    private Statistics execCalculateStatsStep(StableStatsStepConfig config) throws InterruptedException {
        logInfoAndStdOut("Calculating stable statistics for worker count: %d", mWorkersPool.getWorkerCount());

        mStatsCalculator = Statistics.calculator();
        waitReportingStatus(config.getWaitTimeMin(), TimeUnit.MINUTES);
        return mStatsCalculator.calculate();
    }

    private void waitReportingStatus(long timeout, TimeUnit unit) throws InterruptedException {
        TimeCondition endCondition = TimeCondition.untilAfter(timeout, unit);
        do {
            System.out.format("Waiting: %.1f OPS\n" +
                              "         %.3f percentile\n" +
                              "         %d workers\n" +
                              "         %d threads\n",
                    mWorkersPool.getCurrentOperationsPerSec(),
                    mPercentileCalculator.getCurrentPercentile(),
                    mWorkersPool.getWorkerCount(),
                    mWorkersPool.getThreadCount());
            if (endCondition.await(1, TimeUnit.SECONDS)) break;
            moveBackLines(4);
        } while (true);
    }

    private void setWorkerCount(int count) throws InterruptedException {
        LOGGER.debug("Setting worker count to: {}", count);
        int added = mWorkersPool.setWorkerCount(count);
        Future<?> unblocked = mWorkersPool.workersUnblockedFuture();

        if (added < 0) {
            LOGGER.debug("Awaiting termination of stopped workers...");
            mWorkersPool.awaitStoppedWorkersTermination();
        }
        try {
            MoreFutures.awaitTermination(unblocked, 2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.debug("Awaiting current workers to be unblocked...");
            MoreFutures.awaitTermination(unblocked);
        }

        mPercentileCalculator.reset();
    }

    private boolean isComplying(Message msg) throws InterruptedException {
        FieldDescriptor waitTimeField = msg.getDescriptorForType().findFieldByName("baseWaitTimeSec");
        return isComplying((Long) msg.getField(waitTimeField), TimeUnit.SECONDS);
    }

    private boolean isComplying(long baseWaitTime, TimeUnit unit) throws InterruptedException {
        int samples = mConfig.getComplianceTestSamples();
        double percentileThreshold = mConfig.getPercentileThreshold();
        double confidenceWidth = mConfig.getComplianceTestConfidenceWidth();

        LinkedList<Double> percentiles = Lists.newLinkedList();
        do {
            LOGGER.trace("Compliance check, waiting: {} {}", baseWaitTime, unit);
            unit.sleep(baseWaitTime);

            double percentile = mPercentileCalculator.getCurrentPercentile();
            LOGGER.trace("Current percentile: {} Execution count: {}", percentile, mPercentileCalculator.count());
            percentiles.addLast(percentile);
            if (percentiles.size() < samples) continue;

            double avg = Statistics.doubleStream(percentiles).average().orElse(0);
            double stdDev = Statistics.stdDev(percentiles, avg);
            double confidenceBand = stdDev * confidenceWidth;
            LOGGER.trace("Compliance test: Elms: {} Average: {} StdDev: {}", percentiles, avg, stdDev);

            if (avg - confidenceBand > percentileThreshold) {
                LOGGER.trace("Complies!");
                return true;
            } else if (avg + confidenceBand < percentileThreshold) {
                LOGGER.trace("Doesn't comply!");
                return false;
            }
            percentiles.removeFirst();
        } while (true);
    }

    private static void logInfoAndStdOut(String format, Object... args) {
        String msg = String.format(format, args);
        System.out.println(msg);
        LOGGER.info(msg);
    }

    /**
     * Uses ANSI codes to move back the cursor n lines and clear the console from that point.
     */
    private static void moveBackLines(int nLines) {
        String ESC = "\033[";
        System.out.print(ESC + nLines + "F" + // move back n lines
                ESC + "1G" +         // move caret to start of line
                ESC + "0J");         // clear screen from cursor forward
        System.out.flush();
    }
}
