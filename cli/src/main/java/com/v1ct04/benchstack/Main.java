package com.v1ct04.benchstack;

import com.google.common.base.Stopwatch;
import com.v1ct04.benchstack.driver.Benchmark;
import com.v1ct04.benchstack.driver.BenchmarkAction;
import com.v1ct04.benchstack.webserver.RestfulHttpClient;
import com.v1ct04.benchstack.webserver.WebServerBenchmarkAction;
import com.v1ct04.benchstack.webserver.impl.NingHttpClient;
import com.v1ct04.benchstack.webserver.impl.PokestackWebServerClient;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {
        CommandLineOptions options = CommandLineOptions.parse(args);
        configLogging(options.logLevel, options.logFile);

        try (RestfulHttpClient client = new NingHttpClient(options.serverAddress)) {
            BenchmarkAction action = new WebServerBenchmarkAction(
                    client, PokestackWebServerClient::asyncCreate);
            Benchmark bench = new Benchmark(options.benchmarkConfig, action);

            Stopwatch stopwatch = Stopwatch.createStarted();
            System.out.println("Starting benchmark at: " + new Date());
            bench.start().get();
            System.out.println("Benchmark finished at: " + new Date());
            System.out.format("Elapsed time: %.2f minutes\n", stopwatch.elapsed(TimeUnit.SECONDS) / 60.0);
        }
    }

    private static void configLogging(Level l, String file) throws IOException {
        System.setProperty("org.slf4j.simpleLogger.logFile", file);
        System.setProperty("org.slf4j.simpleLogger.log.com.v1ct04.benchstack", l.toString().toLowerCase());
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "MMM d, yyyy hh:mm:ss aaa");
        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
    }
}
