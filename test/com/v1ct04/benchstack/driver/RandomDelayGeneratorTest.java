package com.v1ct04.benchstack.driver;

import org.junit.Assert;
import org.junit.Test;

import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

public class RandomDelayGeneratorTest {

    @Test
    public void testNonNegative() throws Exception {
        RandomDelayGenerator generator = new RandomDelayGenerator(1, 1);
        for (int i = 0; i < 10; i++) {
            Assert.assertTrue(generator.getAsDouble() >= 0);
        }
    }

    @Test
    public void testAverage() throws Exception {
        for (int degrees = 1; degrees < 6; degrees++) {
            double avg = DoubleStream.generate(new RandomDelayGenerator(1, degrees))
                    .limit(10000)
                    .average()
                    .orElse(0);
            // Not guaranteed to pass, but probability is VERY high
            Assert.assertEquals(avg, 1, 0.05);
        }
    }

    @Test
    public void testLongAverage() throws Exception {
        for (int degrees = 1; degrees < 6; degrees++) {
            double avg = LongStream.generate(new RandomDelayGenerator(1000, degrees))
                    .limit(10000)
                    .average()
                    .orElse(0);
            Assert.assertEquals(avg, 1000, 50);
        }
    }
}
