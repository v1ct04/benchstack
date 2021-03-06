package com.v1ct04.benchstack.driver;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.v1ct04.benchstack.concurrent.ForwardingScheduledExecutorService;
import com.v1ct04.benchstack.concurrent.MoreFutures;
import com.v1ct04.benchstack.concurrent.ReschedulingTask;
import com.v1ct04.benchstack.concurrent.Signaler;

import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

class ConcurrentWorkersPool {

    private static final AtomicInteger sPoolNumber = new AtomicInteger(0);

    private final IntConsumer mWorkerFunction;
    private final ThreadPoolExecutor mThreadPoolExecutor;
    private final ScheduledExecutorService mExecutor;

    private final Stack<ReschedulingTask> mWorkers = new Stack<>();
    private final Queue<ReschedulingTask> mStoppedWorkers = new ConcurrentLinkedQueue<>();

    private final Stopwatch mSinceLastChange = Stopwatch.createUnstarted();
    private final AtomicInteger mOperationCount = new AtomicInteger(0);
    private final Signaler mTasksResetter = new Signaler();

    public ConcurrentWorkersPool(IntConsumer workerFunction) {
        String threadNameFormat = String.format(
                "workers-pool-%d-thread-%%d", sPoolNumber.getAndIncrement());
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(threadNameFormat)
                .setDaemon(true)
                .build();

        mThreadPoolExecutor = new ThreadPoolExecutor(5, Integer.MAX_VALUE,
                                                     30, TimeUnit.SECONDS,
                                                     new SynchronousQueue<>(),
                                                     threadFactory);
        mExecutor = new ForwardingScheduledExecutorService(mThreadPoolExecutor,
                                                           threadFactory);
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
        mExecutor.shutdownNow();
    }

    public void awaitTermination() throws InterruptedException {
        awaitStoppedWorkersTermination();
        while (!mExecutor.awaitTermination(1, TimeUnit.DAYS));
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
            mStoppedWorkers.offer(worker);
            count--;
        }
    }

    public void awaitStoppedWorkersTermination() throws InterruptedException {
        ReschedulingTask stopped = mStoppedWorkers.poll();
        while (stopped != null) {
            stopped.awaitTermination();
            stopped = mStoppedWorkers.poll();
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
