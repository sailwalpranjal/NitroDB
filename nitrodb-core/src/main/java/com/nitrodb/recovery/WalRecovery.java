package com.nitrodb.recovery;

import com.nitrodb.memtable.MemTable;
import com.nitrodb.wal.WalReader;
import com.nitrodb.wal.WalRecord;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WalRecovery {

    public long replay(List<Path> walFiles, MemTable target, long fromSeq, com.nitrodb.DBConfig.WalCorruptionPolicy policy) {
        long maxRecovered = fromSeq;
        List<WalRecord> batch = new ArrayList<>();
        boolean inBatch = false;
        try (WalReader reader = new WalReader(walFiles, policy)) {
            while (reader.hasNext()) {
                WalRecord record = reader.readNext().orElseThrow();
                if (record.sequenceNumber() <= fromSeq) {
                    continue;
                }
                switch (record.type()) {
                    case BATCH_START -> {
                        batch.clear();
                        inBatch = true;
                    }
                    case BATCH_END -> {
                        if (inBatch) {
                            for (WalRecord batchRecord : batch) {
                                maxRecovered = Math.max(maxRecovered, apply(target, batchRecord));
                            }
                        }
                        batch.clear();
                        inBatch = false;
                    }
                    case PUT, DELETE -> {
                        if (inBatch) {
                            batch.add(record);
                        } else {
                            maxRecovered = Math.max(maxRecovered, apply(target, record));
                        }
                    }
                }
            }
        }
        return maxRecovered;
    }

    private long apply(MemTable target, WalRecord record) {
        if (record.type() == WalRecord.RecordType.DELETE) {
            target.putTombstone(record.key(), record.sequenceNumber());
        } else {
            target.put(record.key(), record.value(), record.sequenceNumber());
        }
        return record.sequenceNumber();
    }
}
