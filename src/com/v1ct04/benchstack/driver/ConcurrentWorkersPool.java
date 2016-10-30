package com.v1ct04.benchstack.driver;

import com.v1ct04.benchstack.concurrent.VariantScheduledExecutorService;

import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

class ConcurrentWorkersPool {

    private final Runnable mWorkerFunction;
    private final VariantScheduledExecutorService mExecutorService;

    private final AtomicInteger mWorkerCount = new AtomicInteger(0);
    private final Stack<Future<?>> mWorkers = new Stack<>();

    public ConcurrentWorkersPool(Runnable workerFunction) {
        mExecutorService = new VariantScheduledExecutorService(Executors.newCachedThreadPool());
        mWorkerFunction = workerFunction;
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
        if (toAdd > 0) addWorkersImpl(toAdd);
        else removeWorkersImpl(-toAdd);
    }

    public int addWorkers(int n) {
        int size = mWorkerCount.addAndGet(n);
        addWorkersImpl(n);
        return size;
    }

    public int removeWorkers(int n) {
        int size = mWorkerCount.addAndGet(-n);
        removeWorkersImpl(n);
        return size;
    }

    private void addWorkersImpl(int n) {
        Stream.generate(this::newWorker)
                .limit(n)
                .forEach(mWorkers::push);
    }

    private void removeWorkersImpl(int n) {
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
        return mExecutorService.scheduleAtVariableRate(
                mWorkerFunction,
                0, new RandomDelayGenerator(1000),
                TimeUnit.MILLISECONDS);
    }
}
