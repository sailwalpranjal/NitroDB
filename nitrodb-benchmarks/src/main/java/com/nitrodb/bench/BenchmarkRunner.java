package com.nitrodb.bench;

import org.openjdk.jmh.Main;

public final class BenchmarkRunner {

    private BenchmarkRunner() {
    }

    /**
     * Runs the packaged JMH benchmark suite.
     *
     * @param args command-line benchmark filters and options
     * @throws Exception if JMH initialization fails
     */
    public static void main(final String[] args) throws Exception {
        Main.main(args);
    }
}
