package com.v1ct04.benchstack.concurrent;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;

import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class ReschedulingTask
        extends AbstractFuture<Void>
        implements ListenableScheduledFuture<Void>, Runnable {

    public static Builder builder(ScheduledExecutorService executor) {
        return new Builder(executor);
    }

    private final ScheduledExecutorService mExecutor;
    private final Runnable mCommand;
    private final LongSupplier mDelaySupplier;
    private final TimeUnit mUnit;

    private volatile ScheduledFuture<?> mNextExecution;

    private ReschedulingTask(ScheduledExecutorService executor,
                     Runnable command,
                     long initialDelay,
                     TimeUnit initialDelayUnit,
                     LongSupplier delaySupplier,
                     TimeUnit unit) {
        mExecutor = executor;
        mCommand = command;
        mDelaySupplier = delaySupplier;
        mUnit = unit;

        // initial schedule
        mNextExecution = executor.schedule(this, initialDelay, initialDelayUnit);
        if (mDelaySupplier instanceof RateToNanoDelaySupplier) {
            ((RateToNanoDelaySupplier) mDelaySupplier).mLastNanoStartTime =
                    System.nanoTime() + mNextExecution.getDelay(TimeUnit.NANOSECONDS);
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

    private void reschedule() {
        if (!isDone() && !mNextExecution.isCancelled()) {
            mNextExecution = mExecutor.schedule(this, mDelaySupplier.getAsLong(), mUnit);
        }
    }

    public static class Builder {

        private final ScheduledExecutorService mExecutorService;

        private long mInitialDelay = 0;
        private TimeUnit mInitialDelayUnit = TimeUnit.SECONDS;

        private LongSupplier mDelaySupplier = () -> 0;
        private TimeUnit mUnit = TimeUnit.SECONDS;

        private Builder(ScheduledExecutorService executorService) {
            mExecutorService = executorService;
        }

        public Builder setInitialDelay(long initialDelay, TimeUnit unit) {
            mInitialDelay = initialDelay;
            mInitialDelayUnit = unit;
            return this;
        }

        public Builder setFixedDelay(long delay, TimeUnit unit) {
            return setVariableDelay(() -> delay, unit);
        }

        public Builder setFixedRate(long rate, TimeUnit unit) {
            return setVariableRate(() -> rate, unit);
        }

        public Builder setVariableRate(LongSupplier rateSupplier, TimeUnit unit) {
            return setVariableRate(rateSupplier, unit, null);
        }

        public Builder setVariableRate(LongSupplier rateSupplier, TimeUnit unit, Signaler signaler) {
            return setVariableDelay(
                    new RateToNanoDelaySupplier(rateSupplier, unit, signaler),
                    TimeUnit.NANOSECONDS);
        }

        public Builder setVariableDelay(LongSupplier delaySupplier, TimeUnit unit) {
            mDelaySupplier = delaySupplier;
            mUnit = unit;
            return this;
        }

        public ReschedulingTask start(Runnable command) {
            return new ReschedulingTask(
                    mExecutorService, command, mInitialDelay, mInitialDelayUnit, mDelaySupplier, mUnit);
        }
    }

    private static class RateToNanoDelaySupplier implements LongSupplier {

        private long mLastNanoStartTime;

        private final LongSupplier mRateSupplier;
        private final TimeUnit mUnit;
        private final Signaler.Receiver mResetReceiver;

        private RateToNanoDelaySupplier(LongSupplier rateSupplier, TimeUnit unit, Signaler resetter) {
            mRateSupplier = rateSupplier;
            mUnit = unit;
            mResetReceiver = resetter == null ? null : resetter.receiver();
        }

        @Override
        public long getAsLong() {
            if (mResetReceiver != null && mResetReceiver.signaled()) {
                mLastNanoStartTime = System.nanoTime();
            }
            mLastNanoStartTime += mUnit.toNanos(mRateSupplier.getAsLong());
            return mLastNanoStartTime - System.nanoTime();
        }
    }
}
