package com.nitrodb.iter;

import com.nitrodb.memtable.MemTableEntry;

public interface InternalIterator extends AutoCloseable {

    void seek(byte[] key, long maxSeq);

    boolean hasNext();

    MemTableEntry next();

    @Override
    void close();
}
