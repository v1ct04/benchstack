package com.v1ct04.benchstack.driver;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.ListenableFuture;
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

import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Benchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(Benchmark.class);

    private final BenchmarkConfig mConfig;
    private final BenchmarkAction mAction;
    private final PercentileCalculator mPercentileCalculator;
    private final AtomicBoolean mStarted = new AtomicBoolean(false);

    private ConcurrentWorkersPool mWorkersPool;
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
        if (mStarted.getAndSet(true)) {
            throw new IllegalStateException("Benchmark already started");
        }
        return MoreFutures.execAsync(this::executeBenchmark);
    }

    private Statistics executeBenchmark() throws InterruptedException {
        mWorkersPool = new ConcurrentWorkersPool(this::workerFunction);
        try {
            logInfoAndStdOut("Starting Benchmark.");

            Range<Integer> searchLimits = execExponentialLoadStep(mConfig.getExponentialStepConfig());

            execBinarySearchStep(mConfig.getBinarySearchConfig(), searchLimits);

            do {
                double score = execFineTuneStep(mConfig.getFineTuneConfig());

                if (score > 0) {
                    break;
                } else if (score < -2 * mConfig.getFineTuneConfig().getInitialStep()) {
                    logInfoAndStdOut("Unexpectedly bad result from fine tune, doing binary search again.");
                    searchLimits = searchLimits.intersection(Range.atMost(mWorkersPool.getWorkerCount()));
                    execBinarySearchStep(mConfig.getBinarySearchConfig(), searchLimits);
                } else {
                    logInfoAndStdOut("Fine tune result uncompliant, will try again.");
                }
            } while (true);

            Statistics stats = execCalculateStatsStep(mConfig.getStableStatsConfig());
            printFinalResults(stats);
            return stats;
        } catch (InterruptedException e) {
            logInfoAndStdOut("Benchmark interrupted.");
            throw e;
        } catch (Throwable t) {
            LOGGER.error("Benchmark failed with exception: {}", t);
            throw t;
        } finally {
            mWorkersPool.shutdown();
            mWorkersPool = null;
            logInfoAndStdOut("Finished Benchmark.");
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

    private double execFineTuneStep(FineTuneStepConfig config) throws InterruptedException {
        logInfoAndStdOut("Starting fine tune step.");

        double score = complianceScore(config);
        if (score < 0) {
            int tuneDownStep = 2 * (int) Math.min(Math.round(-score), config.getInitialStep());
            do {
                LOGGER.debug("Fine tuning down with step: {}", tuneDownStep);
                setWorkerCount(Math.max(mWorkersPool.getWorkerCount() - tuneDownStep, 0));
            } while (!isComplying(config));
        }

        int complyingCount = mWorkersPool.getWorkerCount();
        for (int step = config.getInitialStep(); step > 1; step /= 2) {
            while (true) {
                LOGGER.debug("Fine tuning up with step: {}", step);
                setWorkerCount(complyingCount + step);
                if (isComplying(config)) {
                    complyingCount += step;
                } else {
                    break;
                }
            }
        }
        setWorkerCount(complyingCount);
        logInfoAndStdOut("Finished fine tuning. Workers: %d", mWorkersPool.getWorkerCount());
        return complianceScore(config);
    }

    private Statistics execCalculateStatsStep(StableStatsStepConfig config) throws InterruptedException {
        logInfoAndStdOut("Calculating stable statistics for worker count: %d", mWorkersPool.getWorkerCount());
        logInfoAndStdOut("Wait time: %d minutes", config.getWaitTimeMin());

        mPercentileCalculator.reset();
        mStatsCalculator = Statistics.calculator();
        try {
            waitReportingStatus(config.getWaitTimeMin(), TimeUnit.MINUTES);
            return mStatsCalculator.calculate();
        } finally {
            mStatsCalculator = null;
        }
    }

    private void printFinalResults(Statistics stats) {
        logInfoAndStdOut("Final statistics: %s", stats.toString());

        double percentile = mConfig.getPercentileThreshold();
        long delayMillis = mConfig.getDelayLimitMillis();
        logInfoAndStdOut("%dth percentile: %.3f", (int) (100 * percentile), stats.getPercentileValue(percentile));
        logInfoAndStdOut("%dms percentile rank: %.3f", delayMillis, stats.getPercentileRank(delayMillis / 1000.0));
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

            if (endCondition.await(3, TimeUnit.SECONDS)) {
                LOGGER.debug("Finished waiting. Final OPS: {} Percentile: {}",
                        mWorkersPool.getCurrentOperationsPerSec(),
                        mPercentileCalculator.getCurrentPercentile());
                break;
            }
            moveBackLines(4);
        } while (true);
    }

    private void setWorkerCount(int count) throws InterruptedException {
        if (count == mWorkersPool.getWorkerCount()) return;

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
            LOGGER.trace("Workers blocked! Waiting...");
            MoreFutures.awaitTermination(unblocked);
        }

        mPercentileCalculator.reset();
    }

    private boolean isComplying(Message msg) throws InterruptedException {
        return complianceScore(msg) > 0;
    }

    private double complianceScore(Message msg) throws InterruptedException {
        FieldDescriptor waitTimeField = msg.getDescriptorForType().findFieldByName("baseWaitTimeSec");
        return complianceScore((Long) msg.getField(waitTimeField), TimeUnit.SECONDS);
    }

    /**
     * Score is >=1 if complies or <=-1 if not, and the higher the absolute value the further it is
     * from the percentile threshold.
     */
    private double complianceScore(long baseWaitTime, TimeUnit unit) throws InterruptedException {
        int samples = mConfig.getComplianceTestSamples();
        double percentileThreshold = mConfig.getPercentileThreshold();
        double confidenceWidth = mConfig.getComplianceTestConfidenceWidth();

        LinkedList<Double> percentiles = Lists.newLinkedList();
        do {
            LOGGER.trace("Compliance check, waiting: {} {}", baseWaitTime, unit);
            Future<?> unblocked = mWorkersPool.workersUnblockedFuture();
            unit.sleep(baseWaitTime);
            if (!unblocked.isDone()) {
                LOGGER.trace("Workers blocked! Waiting...");
                MoreFutures.awaitTermination(unblocked);
            }

            double percentile = mPercentileCalculator.getCurrentPercentile();
            LOGGER.trace("Current percentile: {} Execution count: {}", percentile, mPercentileCalculator.count());
            percentiles.addLast(percentile);
            if (percentiles.size() < samples) continue;

            double avg = Statistics.doubleStream(percentiles).average().orElse(0);
            double stdDev = Statistics.stdDev(percentiles, avg);
            double confidenceBand = stdDev * confidenceWidth;
            LOGGER.trace("Compliance test: Elms: {} Average: {} StdDev: {}", percentiles, avg, stdDev);

            if (percentile >= percentileThreshold) {
                if (avg - confidenceBand >= percentileThreshold) {
                    double score = Math.abs(Math.log(percentileThreshold) / Math.log(percentile));
                    LOGGER.debug("Complies! Score: {}", score);
                    return score;
                }
            } else if (avg + confidenceBand < percentileThreshold) {
                double score = -Math.abs(Math.log(percentile) / Math.log(percentileThreshold));
                LOGGER.debug("Doesn't comply! Score: {}", score);
                return score;
            }
            percentiles.removeFirst();
        } while (true);
    }

    private static void logInfoAndStdOut(String format, Object... args) {
        String msg = String.format(format, args);
        System.out.println(new Date() + " " + msg);
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
