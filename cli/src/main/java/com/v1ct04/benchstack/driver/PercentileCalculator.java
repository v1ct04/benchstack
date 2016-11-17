package com.v1ct04.benchstack.driver;

import java.util.concurrent.atomic.AtomicLong;

public class PercentileCalculator {

    private final double mBound;

    private final AtomicLong mCurrentExecuting = new AtomicLong(0);
    private final AtomicLong mCurrentLower = new AtomicLong(0);
    private final AtomicLong mCurrentHigher = new AtomicLong(0);

    public PercentileCalculator(double bound) {
        mBound = bound;
    }

    public void startExecution() {
        mCurrentExecuting.incrementAndGet();
    }

    public void finishExecution() {
        mCurrentExecuting.decrementAndGet();
    }

    public void appendValue(double value) {
        if (value <= mBound) {
            mCurrentLower.incrementAndGet();
        } else {
            mCurrentHigher.incrementAndGet();
        }
    }

    public double getCurrentPercentile() {
        long lower = mCurrentLower.get(); // read lower first for a pessimistic approach
        long total = lower + mCurrentHigher.get() + mCurrentExecuting.get();

        if (total == 0) return 1;
        return lower / (double) total;
    }

    public long count() {
        return mCurrentLower.get() + mCurrentHigher.get();
    }

    public void reset() {
        mCurrentHigher.set(0);
        mCurrentLower.set(0);
    }
}
