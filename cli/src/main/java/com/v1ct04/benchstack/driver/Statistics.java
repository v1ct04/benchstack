package com.v1ct04.benchstack.driver;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;

public class Statistics {

    public static Calculator calculator() {
        return new Calculator();
    }

    public static DoubleStream doubleStream(Collection<Double> convertible) {
        return convertible.stream().mapToDouble(d -> d);
    }

    public static double variance(Collection<Double> values, double avg) {
        return doubleStream(values)
                .map(d -> (d - avg))
                .map(d -> d * d)
                .average()
                .orElse(0);
    }

    public static double stdDev(Collection<Double> values, double avg) {
        return Math.sqrt(variance(values, avg));
    }

    public final DoubleSummaryStatistics summary;
    public final double variance;
    public final double stdDev;
    public final double samplesPerSec;

    private final List<Double> mValues;

    private Statistics(List<Double> values, long elapsedTimeSec) {
        mValues = values;
        Collections.sort(mValues);

        summary = doubleStream(mValues).summaryStatistics();

        variance = variance(mValues, summary.getAverage());
        stdDev = Math.sqrt(variance);
        samplesPerSec = (summary.getCount() / (double) elapsedTimeSec);
    }

    public double getPercentileValue(double percentile) {
        if (percentile < 0 || percentile > 1) {
            throw new IllegalArgumentException("Percentile must be between 0 and 1");
        }
        return mValues.get((int) (percentile * (mValues.size() - 1)));
    }

    public double getPercentileRank(double value) {
        int idx = Collections.binarySearch(mValues, value);
        if (idx < 0) idx = -idx;
        while (idx < mValues.size() && mValues.get(idx) <= value) idx++;
        return idx / (double) mValues.size();
    }

    @Override
    public String toString() {
        return "Statistics{" +
                "summary=" + summary +
                ", variance=" + variance +
                ", stdDev=" + stdDev +
                ", samplesPerSec=" + samplesPerSec +
                '}';
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
