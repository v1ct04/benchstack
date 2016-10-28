package com.v1ct04.benchstack.futures;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

public class VariantScheduledExecutorService extends AbstractListeningExecutorService implements ListeningScheduledExecutorService {

    private final ListeningScheduledExecutorService mScheduledExecutor;
    private final ListeningExecutorService mDelegatedExecutor;

    // Constructors

    public VariantScheduledExecutorService(ExecutorService delegatedExecutor) {
        this(delegatedExecutor, Executors.defaultThreadFactory());
    }

    public VariantScheduledExecutorService(ExecutorService delegatedExecutor, ThreadFactory factory) {
        mScheduledExecutor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1, factory));
        mDelegatedExecutor = MoreExecutors.listeningDecorator(mScheduledExecutor);
    }

    // AbstractListeningExecutorService

    @Override
    public void shutdown() {
        mScheduledExecutor.shutdown();
        mDelegatedExecutor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> r = mScheduledExecutor.shutdownNow();
        r.addAll(mDelegatedExecutor.shutdownNow());
        return r;
    }

    @Override
    public boolean isShutdown() {
        return mDelegatedExecutor.isShutdown();
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
        return scheduleAtVariableRate(command, initialDelay, () -> period, unit);
    }

    @Override
    public ListenableScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduleWithVariableDelay(command, initialDelay, () -> delay, unit);
    }

    // New Public API

    public ListenableScheduledFuture<?> scheduleAtVariableRate(Runnable command, long initialDelay, LongSupplier rateSupplier, TimeUnit unit) {
        return scheduleWithVariableDelay(command, unit.toNanos(initialDelay), new RateToNanoDelaySupplier(initialDelay, rateSupplier, unit), TimeUnit.NANOSECONDS);
    }

    public ListenableScheduledFuture<?> scheduleWithVariableDelay(Runnable command, long initialDelay, LongSupplier delaySupplier, TimeUnit unit) {
        return new ListenableReschedulingTask(command, initialDelay, delaySupplier, unit);
    }

    // Private Helpers

    private final class ListenableReschedulingTask
            extends AbstractFuture<Void>
            implements ListenableScheduledFuture<Void>, Runnable {

        private final Runnable mCommand;
        private final LongSupplier mDelaySupplier;
        private final TimeUnit mUnit;

        private volatile ScheduledFuture<?> mNextExecution;

        private ListenableReschedulingTask(Runnable command, long initialDelay, LongSupplier delaySupplier, TimeUnit unit) {
            mCommand = command;
            mDelaySupplier = delaySupplier;
            mUnit = unit;

            // initial schedule
            mNextExecution = schedule(this, initialDelay, mUnit);
        }

        private void reschedule() {
            if (!isDone() && !mNextExecution.isCancelled()) {
                mNextExecution = schedule(this, mDelaySupplier.getAsLong(), mUnit);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            mNextExecution.cancel(mayInterruptIfRunning);
            return super.cancel(mayInterruptIfRunning);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return mNextExecution.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return mNextExecution.compareTo(o);
        }

        @Override
        public void run() {
            try {
                mCommand.run();
                reschedule();
            } catch (Throwable t) {
                setException(t);
                throw Throwables.propagate(t);
            }
        }
    }

    private static class RateToNanoDelaySupplier implements LongSupplier {

        private long mNextNanoStartTime;

        private final LongSupplier mRateSupplier;
        private final TimeUnit mUnit;

        private RateToNanoDelaySupplier(long initialDelay, LongSupplier rateSupplier, TimeUnit unit) {
            mNextNanoStartTime = System.nanoTime() + unit.toNanos(initialDelay);
            mRateSupplier = rateSupplier;
            mUnit = unit;
        }

        @Override
        public long getAsLong() {
            mNextNanoStartTime += mUnit.toNanos(mRateSupplier.getAsLong());
            return mNextNanoStartTime - System.nanoTime();
        }
    }
}
