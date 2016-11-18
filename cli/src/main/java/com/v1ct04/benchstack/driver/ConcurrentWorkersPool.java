package com.v1ct04.benchstack.driver;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.v1ct04.benchstack.concurrent.ForwardingScheduledExecutorService;
import com.v1ct04.benchstack.concurrent.MoreFutures;
import com.v1ct04.benchstack.concurrent.ReschedulingTask;
import com.v1ct04.benchstack.concurrent.Signaler;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

class ConcurrentWorkersPool {

    private final IntConsumer mWorkerFunction;
    private final ThreadPoolExecutor mThreadPoolExecutor;
    private final ScheduledExecutorService mExecutor;

    private final Stack<ReschedulingTask> mWorkers = new Stack<>();
    private volatile List<ReschedulingTask> mStoppedWorkers = Lists.newLinkedList();

    private final Stopwatch mSinceLastChange = Stopwatch.createUnstarted();
    private final AtomicInteger mOperationCount = new AtomicInteger(0);
    private final Signaler mTasksResetter = new Signaler();

    public ConcurrentWorkersPool(IntConsumer workerFunction) {
        mThreadPoolExecutor = new ThreadPoolExecutor(5, Integer.MAX_VALUE, 5, TimeUnit.SECONDS, new SynchronousQueue<>());
        mExecutor = new ForwardingScheduledExecutorService(mThreadPoolExecutor);
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
        mExecutor.shutdown();
    }

    public synchronized int getWorkerCount() {
        return mWorkers.size();
    }

    public synchronized int setWorkerCount(int count) {
        int toAdd = (count - mWorkers.size());
        if (toAdd > 0) addWorkers(toAdd);
        else removeWorkers(-toAdd);

        mTasksResetter.signal();
        mOperationCount.set(0);
        mSinceLastChange.reset().start();
        return toAdd;
    }

    private synchronized void addWorkers(int count) {
        while (count > 0) {
            mWorkers.push(newWorker(mWorkers.size()));
            count--;
        }
    }

    private synchronized void removeWorkers(int count) {
        while (count > 0) {
            ReschedulingTask worker = mWorkers.pop();
            worker.cancel(false);
            mStoppedWorkers.add(worker);
            count--;
        }
    }

    public void awaitStoppedWorkersTermination() throws InterruptedException {
        // avoid awaiting in a synchronized context
        List<ReschedulingTask> toAwait;
        synchronized (this) {
            toAwait = mStoppedWorkers;
            mStoppedWorkers = Lists.newLinkedList();
        }

        for (ReschedulingTask task : toAwait) {
            task.awaitTerminationInterruptibly();
        }
    }

    public synchronized ListenableFuture<?> workersUnblockedFuture() {
        Iterable<ListenableFuture<Boolean>> futures = mWorkers.stream()
                .map(ReschedulingTask::nextExecutionFuture)
                .map(MoreFutures::toSuccessFuture)
                ::iterator;
        return Futures.allAsList(futures);
    }

    /**
     * Creates a new worker in the underlying executor service with a
     * variable execution rate, so that we don't have bursts of requests
     * to the SUT, distributing them over the testing period.
     */
    private ReschedulingTask newWorker(int id) {
        LongSupplier delayGenerator = new RandomDelayGenerator(1000, 4);
        return ReschedulingTask.builder(mExecutor)
                .setInitialDelay(delayGenerator.getAsLong(), TimeUnit.MILLISECONDS)
                .setVariableRate(delayGenerator, TimeUnit.MILLISECONDS, mTasksResetter)
                .start(() -> {
                    mOperationCount.incrementAndGet();
                    mWorkerFunction.accept(id);
                });
    }
}
