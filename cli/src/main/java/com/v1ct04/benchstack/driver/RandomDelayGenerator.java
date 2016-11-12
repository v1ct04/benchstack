package com.v1ct04.benchstack.driver;

import java.util.Random;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Uses a chi-squared distribution with a certain degree of freedom for
 * generating randomized delays with a specified mean. A chi-squared
 * distribution is known to have a mean equal to the number of degrees
 * of freedom, thus we only need to sum the square of a couple of gaussian
 * random values, multiplying it by the desired mean divided by the number
 * of degrees of freedom, obtaining then the desired distribution.
 */
public class RandomDelayGenerator implements DoubleSupplier, LongSupplier {

    private final Random mRandom = new Random();
    private final double mMultiplier;
    private final int mDegreesOfFreedom;

    public RandomDelayGenerator(double mean, int degreesOfFreedom) {
        mMultiplier = mean / degreesOfFreedom;
        mDegreesOfFreedom = degreesOfFreedom;
    }

    @Override
    public long getAsLong() {
        return (long) getAsDouble();
    }

    @Override
    public double getAsDouble() {
        double chiSq = 0;
        for (int i = 0; i < mDegreesOfFreedom; i++) {
            double gaussian = mRandom.nextGaussian();
            chiSq += gaussian * gaussian;
        }
        return chiSq * mMultiplier;
    }
}
