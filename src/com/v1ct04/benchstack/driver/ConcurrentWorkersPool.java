package com.v1ct04.benchstack.driver;

import com.v1ct04.benchstack.futures.VariantScheduledExecutorService;

import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Stream;

class ConcurrentWorkersPool {

    private final IntFunction mWorkerFunction;
    private final VariantScheduledExecutorService mExecutorService;
    private final Stack<Future<?>> mWorkers = new Stack<>();

    public ConcurrentWorkersPool(IntFunction workerFunction) {
        mWorkerFunction = workerFunction;
        mExecutorService = new VariantScheduledExecutorService(Executors.newCachedThreadPool());
    }

    public int getWorkerCount() {
        return mWorkers.size();
    }

    public synchronized void setWorkerCount(int count) {
        int toAdd = count - mWorkers.size();
        if (toAdd > 0) addWorkers(toAdd);
        else removeWorkers(-toAdd);
    }

    public synchronized void addWorkers(int n) {
        Stream.generate(mWorkers::size)
                .map(this::newWorker)
                .limit(n)
                .forEach(mWorkers::add);
    }

    public synchronized void removeWorkers(int n) {
        Stream.generate(mWorkers::pop)
                .limit(n)
                .forEach(w -> w.cancel(false));
    }

    private Future<?> newWorker(int id) {
        return mExecutorService.scheduleAtFixedRate(() -> mWorkerFunction.apply(id), 0, 1, TimeUnit.SECONDS);
    }
}
