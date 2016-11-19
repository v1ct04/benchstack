package com.v1ct04.benchstack.concurrent;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.*;

import java.util.concurrent.*;
import java.util.function.LongSupplier;

public final class ReschedulingTask
        extends AbstractFuture<Void>
        implements ListenableScheduledFuture<Void> {

    public static Builder builder(ScheduledExecutorService executor) {
        return new Builder(executor);
    }

    private final ListeningScheduledExecutorService mExecutor;
    private final Runnable mCommand;
    private final LongSupplier mDelaySupplier;
    private final TimeUnit mUnit;

    private volatile ListenableScheduledFuture<?> mNextExecution;
    private final Phaser mExecutionPhaser = new Phaser() {
        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
            // Terminate the Phaser only if this Task has been cancelled
            // (or failed) and there are no registered parties (a running
            // execution will be a registered party).
            return isDone() && registeredParties == 0;
        }
    };

    private ReschedulingTask(ScheduledExecutorService executor,
                     Runnable command,
                     long initialDelay,
                     TimeUnit initialDelayUnit,
                     LongSupplier delaySupplier,
                     TimeUnit unit) {
        mExecutor = MoreExecutors.listeningDecorator(executor);
        mCommand = command;
        mDelaySupplier = delaySupplier;
        mUnit = unit;

        // initial schedule
        mNextExecution = mExecutor.schedule(this::runAndReschedule, initialDelay, initialDelayUnit);
        if (mDelaySupplier instanceof RateToNanoDelaySupplier) {
            ((RateToNanoDelaySupplier) mDelaySupplier).mLastNanoStartTime =
                    System.nanoTime() + mNextExecution.getDelay(TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (!super.cancel(mayInterruptIfRunning)) return false;

        mExecutionPhaser.register();
        mExecutionPhaser.arriveAndDeregister();
        // At this point the Phaser is either in the terminated state or
        // has one registered party and will terminate when it de-registers
        // itself. This other party can only be an execution of this task,
        // thus the Phaser will terminate when the last execution finishes.

        mNextExecution.cancel(mayInterruptIfRunning);
        return true;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return mNextExecution.getDelay(unit);
    }

    @Override
    public int compareTo(Delayed o) {
        return mNextExecution.compareTo(o);
    }

    /**
     * Whether this task has been cancelled and the last execution has already finished.
     */
    public boolean isTerminated() {
        return mExecutionPhaser.isTerminated();
    }

    public void awaitTermination() {
        while (awaitExecution());
    }

    public void awaitTerminationInterruptibly() throws InterruptedException {
        while (awaitExecutionInterruptibly());
    }

    /**
     * Await one execution to terminate or return false immediately if already cancelled
     * and not running.
     * @return False if this task has been terminated (cancelled) and won't run again.
     */
    public boolean awaitExecution() {
        return mExecutionPhaser.awaitAdvance(mExecutionPhaser.getPhase()) >= 0;
    }

    public boolean awaitExecutionInterruptibly() throws InterruptedException {
        return mExecutionPhaser.awaitAdvanceInterruptibly(mExecutionPhaser.getPhase()) >= 0;
    }

    public boolean awaitExecutionInterruptibly(long time, TimeUnit unit) throws InterruptedException, TimeoutException {
        return mExecutionPhaser.awaitAdvanceInterruptibly(mExecutionPhaser.getPhase(), time, unit) >= 0;
    }

    public ListenableFuture<?> nextExecutionFuture() {
        return Futures.nonCancellationPropagating(mNextExecution);
    }

    private void runAndReschedule() {
        if (mExecutionPhaser.register() < 0) return;

        try {
            mCommand.run();
        } catch (Throwable t) {
            setException(t);
            throw Throwables.propagate(t);
        } finally {
            mExecutionPhaser.arriveAndDeregister();
        }
        // We can't reschedule in the registered state otherwise it's possible
        // that the Phaser never advances phase, when the next execution
        // starts and registers itself before this one arrives and de-registers.
        reschedule();
    }

    private void reschedule() {
        if (!isDone()) {
            mNextExecution = mExecutor.schedule(this::runAndReschedule, mDelaySupplier.getAsLong(), mUnit);

            // In case #cancel() has been called after the if check and before the mNextExecution
            // update above, avoid keeping the scheduled task in the executor by rechecking if
            // we are done and cancelling the next execution future if so.
            if (isDone()) {
                mNextExecution.cancel(false);
            }
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
