package com.v1ct04.benchstack.driver;

import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Uses a chi-squared distribution with 1 degree of freedom for
 * generating randomized delays with a specified mean. A chi-squared
 * distribution is known to have a mean equal to the number of
 * degrees of freedom (so 1), thus we only need to generate a gaussian
 * random value, square it and multiply by the desired mean and we
 * have the distribution with the desired mean.
 */
class RandomDelayGenerator implements LongSupplier {

    private final Random mRandom = new Random();
    private final double mMean;

    RandomDelayGenerator(double mean) {
        mMean = mean;
    }

    @Override
    public long getAsLong() {
        double stdNormal = mRandom.nextGaussian();
        double chiSq = stdNormal * stdNormal;
        return (long) (chiSq * mMean);
    }
}
