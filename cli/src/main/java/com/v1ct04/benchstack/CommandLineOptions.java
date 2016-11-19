package com.v1ct04.benchstack;

import com.google.common.collect.Lists;
import com.google.protobuf.TextFormat;
import com.v1ct04.benchstack.driver.BenchmarkConfigWrapper.BenchmarkConfig;
import jline.TerminalFactory;
import org.apache.commons.cli.*;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.event.Level;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class CommandLineOptions {

    public static CommandLineOptions parse(String[] args) {
        try {
            return new CommandLineOptions(args);
        } catch (ParseException e) {
            System.err.println("Usage error: " + e.getMessage());
        } catch (Exception e) {
            System.err.print("Error parsing command line option: ");
            e.printStackTrace();
        }
        System.err.println();

        printHelp();
        System.exit(1);
        throw null; // make Java compiler happy
    }

    public final BenchmarkConfig benchmarkConfig;
    public final URI serverAddress;
    public final Level logLevel;
    public final String logFile;

    private static final Options OPTIONS = new Options()
            .addOption(Option.builder("c")
                    .longOpt("configFile")
                    .hasArg()
                    .argName("file")
                    .desc("File with configuration for benchmark in Protocol Buffers text format.")
                    .build())
            .addOption(Option.builder("H")
                    .longOpt("host")
                    .hasArg()
                    .argName("hostname")
                    .desc("Host name of the Pokestack web server to use for the benchmark. Defaults to localhost")
                    .build())
            .addOption(Option.builder("p")
                    .longOpt("port")
                    .hasArg()
                    .argName("port")
                    .desc("Port number to connect to in the specified host. Defaults to 3000 (default Node.js port).")
                    .build())
            .addOption(Option.builder("l")
                    .longOpt("logLevel")
                    .hasArg()
                    .argName("level")
                    .desc("SLF4J level to output to log file. Must be one of: trace, debug, info, warn or error. Default is trace.")
                    .build())
            .addOption(Option.builder("f")
                    .longOpt("logFile")
                    .hasArg()
                    .argName("file")
                    .desc("File where to output logs. Default is benchstack.log. System.out and System.err are available options as well.")
                    .build());

    private static Options HELP_OPTION = new Options().addOption("h", "help", false, "Print this usage guide.");
    static {
        HELP_OPTION.getOptions().forEach(OPTIONS::addOption);
    }
    

    private CommandLineOptions(String[] args) throws ParseException, URISyntaxException, IOException {
        CommandLineParser parser = new DefaultParser();
        if (parser.parse(HELP_OPTION, args, true).hasOption("help")) {
            printHelp();
            System.exit(0);
        }
        CommandLine cmd = parser.parse(OPTIONS, args);

        logLevel = Level.valueOf(cmd.getOptionValue("logLevel", "trace").toUpperCase());
        logFile = cmd.getOptionValue("logFile", "benchstack.log");

        benchmarkConfig = parseConfig(cmd.getOptionValue("configFile"));
        serverAddress = new URIBuilder()
                .setScheme("http")
                .setHost(cmd.getOptionValue("host", "localhost"))
                .setPort(Integer.parseInt(cmd.getOptionValue("port", "3000")))
                .build();
    }

    private static void printHelp() {
        HelpFormatter help = new HelpFormatter();
        help.setOptionComparator(FixedOrderComparator.of(OPTIONS.getOptions()));
        try {
            help.setWidth(TerminalFactory.get().getWidth());
        } catch (Exception e) {
            // ignore any exceptions, this is optional
        }

        help.printHelp(
                "benchstack",
                "\nPerforms a benchmark against an existing server deployed in an OpenStack environment.\n\n",
                OPTIONS,
                "\nOpen source repository: https://github.com/v1ct04/benchstack\n\n",
                true);
    }

    private static BenchmarkConfig parseConfig(String filename) throws IOException {
        BenchmarkConfig.Builder config = BenchmarkConfig.newBuilder();
        if (filename != null) {
            TextFormat.merge(new FileReader(filename), config);
        }
        return config.build();
    }

    private static class FixedOrderComparator<V, A> implements Comparator<V> {

        private final Function<V, A> mExtractor;
        private final List<A> mOrder;

        public static <A> FixedOrderComparator<A, A> of(Iterable<A> order) {
            return new FixedOrderComparator<>(Function.identity(), order);
        }

        private FixedOrderComparator(Function<V, A> extractor, Iterable<A> order) {
            mExtractor = extractor;
            mOrder = Lists.newArrayList(order);
        }

        @Override
        public int compare(V o1, V o2) {
            int i1 = mOrder.indexOf(mExtractor.apply(o1));
            int i2 = mOrder.indexOf(mExtractor.apply(o2));
            if (i1 < 0) i1 = Integer.MAX_VALUE;
            if (i2 < 0) i2 = Integer.MAX_VALUE;
            return i1 - i2;
        }
    }
}
