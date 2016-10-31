package com.v1ct04.benchstack.driver;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Statistics {
    public final DoubleSummaryStatistics summary;
    public final double variance;
    public final double stdDev;
    public final double samplesPerSec;

    private final List<Double> mValues;

    public Statistics(List<Double> values, long elapsedTimeSec) {
        mValues = values;
        Collections.sort(mValues);

        summary = mValues.stream().mapToDouble(d -> d).summaryStatistics();

        variance = mValues.stream()
                .mapToDouble(d -> (d - summary.getAverage()))
                .map(d -> d * d)
                .average()
                .orElse(0);
        stdDev = Math.sqrt(variance);
        samplesPerSec = (summary.getCount() / elapsedTimeSec);
    }

    public double getPercentileValue(double percentile) {
        if (percentile < 0 || percentile > 1) {
            throw new IllegalArgumentException("Percentile must be between 0 and 1");
        }
        return mValues.get((int) (percentile * (mValues.size() - 1)));
    }

    public double getPercentileRank(double value) {
        int idx = Collections.binarySearch(mValues, value);
        while (idx < mValues.size() && mValues.get(idx) == value) idx++;
        return idx / (double) mValues.size();
    }

    public static Calculator calculator() {
        return new Calculator();
    }

    public static class Calculator {
        private final Stopwatch mStopwatch = Stopwatch.createStarted();
        private final List<Double> mValues = Lists.newLinkedList();

        public void appendValue(double value) {
            synchronized (mValues) {
                mValues.add(value);
            }
        }

        public Statistics calculate() {
            List<Double> values;
            synchronized (mValues) {
                values = new ArrayList<>(mValues);
            }
            return new Statistics(values, mStopwatch.elapsed(TimeUnit.SECONDS));
        }
    }
}
