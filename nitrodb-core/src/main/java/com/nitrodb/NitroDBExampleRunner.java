package com.nitrodb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class NitroDBExampleRunner {

    private NitroDBExampleRunner() {
    }

    public static void main(String[] args) {
        Path dataDir = args.length > 0 ? Path.of(args[0]) : Path.of("example-data");
        try (NitroDB db = new NitroDBBuilder().dataDir(dataDir).build()) {
            db.put("alpha".getBytes(StandardCharsets.UTF_8), "one".getBytes(StandardCharsets.UTF_8));
            db.put("beta".getBytes(StandardCharsets.UTF_8), "two".getBytes(StandardCharsets.UTF_8));
            System.out.println("alpha=" + new String(db.get("alpha".getBytes(StandardCharsets.UTF_8)).orElseThrow(), StandardCharsets.UTF_8));
            db.delete("beta".getBytes(StandardCharsets.UTF_8));
        }

        try (NitroDB db = new NitroDBBuilder().dataDir(dataDir).build()) {
            System.out.println("Recovered alpha="
                    + new String(db.get("alpha".getBytes(StandardCharsets.UTF_8)).orElseThrow(), StandardCharsets.UTF_8));
            System.out.println("Recovered beta present="
                    + db.get("beta".getBytes(StandardCharsets.UTF_8)).isPresent());
        }
    }
}
