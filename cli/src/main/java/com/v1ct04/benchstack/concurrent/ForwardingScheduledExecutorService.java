package com.v1ct04.benchstack.concurrent;

import com.google.common.util.concurrent.*;

import java.util.List;
import java.util.concurrent.*;

public class ForwardingScheduledExecutorService extends AbstractListeningExecutorService implements ListeningScheduledExecutorService {

    private final ThreadFactory mThreadFactory;
    private final ListeningScheduledExecutorService mScheduledExecutor;
    private final ListeningExecutorService mDelegatedExecutor;

    // Constructors

    public ForwardingScheduledExecutorService(ExecutorService delegatedExecutor) {
        this(delegatedExecutor, Executors.defaultThreadFactory());
    }

    public ForwardingScheduledExecutorService(ExecutorService delegatedExecutor, ThreadFactory factory) {
        mThreadFactory = factory;
        mScheduledExecutor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1, factory));
        mDelegatedExecutor = MoreExecutors.listeningDecorator(delegatedExecutor);
    }

    // AbstractListeningExecutorService

    @Override
    public void shutdown() {
        mScheduledExecutor.shutdown();
        // We can't shutdown the delegated executor immediately since
        // there might be delayed tasks on the scheduled executor that
        // haven't been sent to the delegated executor yet, spawn a
        // thread to wait for the scheduled executor to shutdown and only
        // then shutdown the delegated executor.
        execAsync(() -> {
            mScheduledExecutor.awaitTermination(1, TimeUnit.DAYS);
            mDelegatedExecutor.shutdown();
        });
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> r = mScheduledExecutor.shutdownNow();
        // Similar to shutdown, but await synchronously in this case,
        // since delayed tasks will be cancelled and only executing
        // tasks will be waited for. Since the scheduled executor tasks
        // are always quick, we can afford waiting for them to finish
        // with no surprises.
        ThrowingRunnable.runPropagating(() -> {
            mScheduledExecutor.awaitTermination(1, TimeUnit.DAYS);
            r.addAll(mDelegatedExecutor.shutdownNow());
        });
        return r;
    }

    @Override
    public boolean isShutdown() {
        return mScheduledExecutor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return mDelegatedExecutor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long startTime = System.nanoTime();
        if (!mScheduledExecutor.awaitTermination(timeout, unit)) {
            return false;
        }
        long remaining = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        return mDelegatedExecutor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }

    @Override
    public void execute(Runnable command) {
        mDelegatedExecutor.execute(command);
    }

    // ListeningScheduledExecutorService

    @Override
    public ListenableScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return MoreFutures.dereference(mScheduledExecutor.schedule(() -> mDelegatedExecutor.submit(command), delay, unit));
    }

    @Override
    public <V> ListenableScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return MoreFutures.dereference(mScheduledExecutor.schedule(() -> mDelegatedExecutor.submit(callable), delay, unit));
    }

    @Override
    public ListenableScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return ReschedulingTask.builder(this)
                .setInitialDelay(initialDelay, unit)
                .setFixedRate(period, unit)
                .start(command);
    }

    @Override
    public ListenableScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return ReschedulingTask.builder(this)
                .setInitialDelay(initialDelay, unit)
                .setFixedDelay(delay, unit)
                .start(command);
    }

    // Private Helpers

    private Thread execAsync(ThrowingRunnable command) {
        Thread t = mThreadFactory.newThread(ThrowingRunnable.propagating(command));
        t.start();
        return t;
    }

}
