package com.nitrodb.bench;

import com.nitrodb.NitroDB;
import com.nitrodb.NitroDBBuilder;
import com.nitrodb.metrics.NoOpMetricsSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class BenchmarkState {

    private NitroDB db;
    private Path dir;

    /**
     * Creates an isolated NitroDB instance for one benchmark trial.
     *
     * @throws IOException if the temporary benchmark directory cannot be created
     */
    @Setup(Level.Trial)
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("nitrodb-bench");
        db = new NitroDBBuilder().dataDir(dir).metricsSink(NoOpMetricsSink.INSTANCE).build();
    }

    /**
     * Closes the database and removes trial-local files.
     *
     * @throws IOException if temporary data cannot be deleted
     */
    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        if (db != null) {
            db.close();
        }
        if (dir != null && Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    protected final NitroDB db() {
        return db;
    }

    protected final Path dir() {
        return dir;
    }
}
