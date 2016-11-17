package com.v1ct04.benchstack;

import com.google.common.base.Stopwatch;
import com.google.protobuf.TextFormat;
import com.v1ct04.benchstack.driver.Benchmark;
import com.v1ct04.benchstack.driver.BenchmarkAction;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig;
import com.v1ct04.benchstack.driver.Statistics;
import com.v1ct04.benchstack.webserver.RestfulHttpClient;
import com.v1ct04.benchstack.webserver.WebServerBenchmarkAction;
import com.v1ct04.benchstack.webserver.impl.NingHttpClient;
import com.v1ct04.benchstack.webserver.impl.PokestackWebServerClient;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.event.Level;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Main {
    private static void configLogging(Level l) throws IOException {
        System.setProperty("org.slf4j.simpleLogger.log." + Benchmark.class.getName(), l.toString().toLowerCase());
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "MMM d, yyyy hh:mm:ss aaa");
        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
    }

    private static BenchmarkConfig parseConfig(String filename) throws IOException {
        BenchmarkConfig.Builder config = BenchmarkConfig.newBuilder();
        TextFormat.merge(new FileReader(filename), config);
        return config.build();
    }

    public static void main(String[] args) throws Exception {
        configLogging(Level.TRACE);

        BenchmarkConfig config = parseConfig("bench.config");
        URI baseUri = new URIBuilder()
                .setScheme("http")
                .setHost("localhost")
                .setPort(3000)
                .build();

        try (RestfulHttpClient client = new NingHttpClient(baseUri)) {
            BenchmarkAction action = new WebServerBenchmarkAction(
                    client, PokestackWebServerClient::asyncCreate);
            Benchmark bench = new Benchmark(config, action);

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
    }
}
