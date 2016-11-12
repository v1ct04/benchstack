package com.v1ct04.benchstack.driver;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.v1ct04.benchstack.concurrent.TimeCondition;
import com.v1ct04.benchstack.concurrent.VariantScheduleExecutor;

import java.util.Stack;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

class ConcurrentWorkersPool {

    private final Runnable mWorkerFunction;
    private final ThreadPoolExecutor mThreadPoolExecutor;
    private final VariantScheduleExecutor mExecutorService;

    private final AtomicInteger mWorkerCount = new AtomicInteger(0);
    private final Stack<Future<?>> mWorkers = new Stack<>();

    private final Stopwatch mSinceLastChange = Stopwatch.createUnstarted();
    private final AtomicInteger mOperationCount = new AtomicInteger(0);

    public ConcurrentWorkersPool(Runnable workerFunction) {
        mThreadPoolExecutor = new ThreadPoolExecutor(5, Integer.MAX_VALUE, 5, TimeUnit.SECONDS, new SynchronousQueue<>());
        mExecutorService = new VariantScheduleExecutor(mThreadPoolExecutor);
        mWorkerFunction = workerFunction;
    }

    public double getCurrentOperationsPerSec() {
        long elapsedMillis = mSinceLastChange.elapsed(TimeUnit.MILLISECONDS);
        if (elapsedMillis == 0) return 0;
        return mOperationCount.get() / (elapsedMillis / 1000.0);
    }

    public int getThreadCount() {
        return mThreadPoolExecutor.getPoolSize();
    }

    public void shutdown() {
        setWorkerCount(0);
        mExecutorService.shutdown();
    }

    public int getWorkerCount() {
        return mWorkerCount.get();
    }

    public void setWorkerCount(int count) {
        int oldCount = mWorkerCount.getAndSet(count);
        int toAdd = (count - oldCount);

        if (toAdd > 0) addWorkers(toAdd);
        else removeWorkers(-toAdd);

        mExecutorService.resetPeriodicTasks();
        mOperationCount.set(0);
        mSinceLastChange.reset().start();
    }

    public boolean awaitStabilization(long timeout, TimeUnit unit) throws InterruptedException {
        TimeCondition timeoutCondition = TimeCondition.untilAfter(timeout, unit);
        long resolutionNanos = TimeUnit.SECONDS.toNanos(1);

        long workerCount = getWorkerCount();
        Range<Double> stableRange = Range.closed(workerCount * 0.95 - 1, workerCount * 1.05 + 1);
        while (!timeoutCondition.isFulfilled()) {
            if (stableRange.contains(getCurrentOperationsPerSec())) {
                return true;
            }
            timeoutCondition.awaitNanos(resolutionNanos);
        }
        return false;
    }

    private void addWorkers(int n) {
        Stream.generate(this::newWorker)
                .limit(n)
                .forEach(mWorkers::push);
    }

    private void removeWorkers(int n) {
        Stream.generate(mWorkers::pop)
                .limit(n)
                .forEach(w -> w.cancel(false));
    }

    /**
     * Creates a new worker in the underlying executor service with a
     * variable execution rate, so that we don't have bursts of requests
     * to the SUT, distributing them over the testing period.
     */
    private Future<?> newWorker() {
        LongSupplier delayGenerator = new RandomDelayGenerator(1000, 4);
        return mExecutorService.scheduleAtVariableRate(
                this::workerFunction,
                delayGenerator.getAsLong(), delayGenerator,
                TimeUnit.MILLISECONDS);
    }

    private void workerFunction() {
        mOperationCount.incrementAndGet();
        mWorkerFunction.run();
    }
}
