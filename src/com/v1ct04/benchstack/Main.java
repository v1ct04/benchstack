package com.v1ct04.benchstack;

import com.google.common.base.Stopwatch;
import com.google.protobuf.TextFormat;
import com.v1ct04.benchstack.driver.Benchmark;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig;
import com.v1ct04.benchstack.driver.Statistics;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.DoubleStream;

public class Main {
    private static void setLogLevel(Level l) {
        Logger base = LogManager.getLogManager().getLogger("");
        base.setLevel(l);
        Arrays.stream(base.getHandlers()).forEach(h -> h.setLevel(l));
    }

    private static BenchmarkConfig parseConfig(String filename) throws IOException {
        BenchmarkConfig.Builder config = BenchmarkConfig.newBuilder();
        TextFormat.merge(new FileReader(filename), config);
        return config.build();
    }

    public static void main(String[] args) throws Exception {
        setLogLevel(Level.FINEST);

        BenchmarkConfig config = parseConfig("bench.config");
        Benchmark bench = new Benchmark(config, Main::sortSumRandom);

        Stopwatch stopwatch = Stopwatch.createStarted();
        System.out.println("Starting benchmark at: " + new Date());
        Statistics stats = bench.start().get();
        System.out.println("Benchmark finished at: " + new Date());
        System.out.format("Elapsed time: %.2f minutes\n", stopwatch.elapsed(TimeUnit.SECONDS) / 60.0);

        System.out.println(stats);
        double percentile = config.getPercentileThreshold();
        long delayMillis = config.getDelayLimitMillis();
        System.out.format("%dth percentile: %.3f\n", (int) (100 * percentile), stats.getPercentileValue(percentile));
        System.out.format("%dms percentile rank: %.2f\n", delayMillis, stats.getPercentileRank(delayMillis / 1000.0));
    }

    private static double sortSumRandom() {
        return DoubleStream.generate(Math::random)
                .limit(10000)
                .sorted()
                .sum();
    }
}
