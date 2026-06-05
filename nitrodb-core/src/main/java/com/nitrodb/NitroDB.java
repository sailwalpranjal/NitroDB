package com.nitrodb;

import com.nitrodb.api.ReadOptions;
import com.nitrodb.api.ScanResult;
import com.nitrodb.api.WriteOptions;
import com.nitrodb.mvcc.Snapshot;
import java.util.Optional;

public interface NitroDB extends AutoCloseable {

    void put(byte[] key, byte[] value);

    void put(byte[] key, byte[] value, WriteOptions options);

    Optional<byte[]> get(byte[] key);

    Optional<byte[]> get(byte[] key, ReadOptions options);

    void delete(byte[] key);

    ScanResult scan(byte[] startKey, byte[] endKey);

    ScanResult scan(byte[] startKey, byte[] endKey, ReadOptions options);

    Snapshot getSnapshot();

    @Override
    void close();
}
