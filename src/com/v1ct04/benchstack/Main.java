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

        Benchmark bench = new Benchmark(parseConfig("bench.config"), Main::sortSumRandom);
        Stopwatch stopwatch = Stopwatch.createStarted();
        System.out.println("Starting benchmark at: " + new Date());
        Statistics stats = bench.start().get();
        System.out.println("Benchmark finished at: " + new Date());
        System.out.println("Elapsed time: " + stopwatch.elapsed(TimeUnit.SECONDS) / 60.0 + " minutes");

        System.out.println(stats);
        System.out.println("95th percentile: " + stats.getPercentileValue(0.95));
        System.out.println("10ms percentile rank: " + stats.getPercentileRank(0.010));
    }

    private static double sortSumRandom() {
        return DoubleStream.generate(Math::random)
                .limit(10000)
                .sorted()
                .sum();
    }
}
