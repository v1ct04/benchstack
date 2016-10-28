package com.v1ct04.benchstack.driver;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

public class RandomDelayGeneratorTest {

    private RandomDelayGenerator mGenerator;

    @Before
    public void setUp() throws Exception {
        mGenerator = new RandomDelayGenerator(1);
    }

    @Test
    public void testNonNegative() throws Exception {
        for (int i = 0; i < 10; i++) {
            Assert.assertTrue(mGenerator.getAsDouble() >= 0);
        }
    }

    @Test
    public void testAverage() throws Exception {
        double avg = DoubleStream.generate(mGenerator)
                .limit(10000)
                .average()
                .orElse(0);
        // Not guaranteed to pass, but probability is VERY high
        Assert.assertEquals(avg, 1, 0.05);
    }

    @Test
    public void testLongAverage() throws Exception {
        double avg = LongStream.generate(new RandomDelayGenerator(1000))
                .limit(10000)
                .average()
                .orElse(0);
        Assert.assertEquals(avg, 1000, 50);
    }
}
