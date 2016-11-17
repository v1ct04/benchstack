package com.v1ct04.benchstack.driver;

public class PercentileCalculator {

    private final double mBound;

    private volatile long mCurrentLower = 0;
    private volatile long mCurrentHigher = 0;

    public PercentileCalculator(double bound) {
        mBound = bound;
    }

    public void appendValue(double value) {
        if (value <= mBound) {
            mCurrentLower++;
        } else {
            mCurrentHigher++;
        }
    }

    public double getCurrentPercentile() {
        long lower = mCurrentLower;
        double total = lower + mCurrentHigher;
        if (total == 0) return 1;
        return lower / total;
    }

    public void reset() {
        mCurrentHigher = mCurrentLower = 0;
    }
}
