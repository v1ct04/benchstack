package com.v1ct04.benchstack.concurrent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public class TimeCondition implements Condition {

    public static TimeCondition untilAfter(long timeout, TimeUnit unit) {
        return new TimeCondition(System.nanoTime() + unit.toNanos(timeout));
    }

    public static TimeCondition until(Date deadline) {
        return new TimeCondition(System.nanoTime() + nanosUntil(deadline));
    }

    private final long mEndNanoTime;

    private TimeCondition(long endNanoTime) {
        mEndNanoTime = endNanoTime;
    }

    @Override
    public void await() throws InterruptedException {
        synchronized (this) {
            TimeUnit.NANOSECONDS.timedWait(this, nanoTimeLeft());
        }
    }

    @Override
    public void awaitUninterruptibly() {
        synchronized (this) {
            boolean interrupted = false;
            while(!isFulfilled()) {
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, nanoTimeLeft());
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public long awaitNanos(long nanosTimeout) throws InterruptedException {
        long startTime = System.nanoTime();
        long timeToWait = mEndNanoTime - startTime;
        if (nanosTimeout < timeToWait) timeToWait = nanosTimeout;

        synchronized (this) {
            TimeUnit.NANOSECONDS.timedWait(this, timeToWait);
        }

        return nanosTimeout - (System.nanoTime() - startTime);
    }

    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        awaitNanos(unit.toNanos(time));
        return isFulfilled();
    }

    @Override
    public boolean awaitUntil(Date deadline) throws InterruptedException {
        awaitNanos(nanosUntil(deadline));
        return isFulfilled();
    }

    @Override
    public void signal() {
        synchronized (this) {
            notify();
        }
    }

    @Override
    public void signalAll() {
        synchronized (this) {
            notifyAll();
        }
    }

    public boolean isFulfilled() {
        return nanoTimeLeft() < 0;
    }

    public long timeLeft(TimeUnit unit) {
        return unit.convert(nanoTimeLeft(), TimeUnit.NANOSECONDS);
    }

    private long nanoTimeLeft() {
        return mEndNanoTime - System.nanoTime();
    }

    private static long nanosUntil(Date deadline) {
        return Instant.now().until(deadline.toInstant(), ChronoUnit.NANOS);
    }
}
