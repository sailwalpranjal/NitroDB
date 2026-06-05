package com.nitrodb.iter;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.memtable.MemTableEntry;
import java.util.Arrays;
import java.util.NoSuchElementException;

public final class IteratorUtils {

    private IteratorUtils() {
    }

    public static InternalIterator skippingTombstones(InternalIterator iterator) {
        return new FilteringIterator(iterator) {
            @Override
            protected boolean include(MemTableEntry entry) {
                return !entry.isTombstone();
            }
        };
    }

    public static InternalIterator deduplicating(InternalIterator iterator) {
        return new InternalIterator() {
            private final java.util.Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();
            private MemTableEntry next;
            private byte[] lastKey;

            @Override
            public void seek(byte[] key, long maxSeq) {
                iterator.seek(key, maxSeq);
                next = null;
                lastKey = null;
            }

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                while (iterator.hasNext()) {
                    MemTableEntry candidate = iterator.next();
                    if (lastKey != null && comparator.compare(candidate.key(), lastKey) == 0) {
                        continue;
                    }
                    lastKey = candidate.key();
                    next = candidate;
                    return true;
                }
                return false;
            }

            @Override
            public MemTableEntry next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                MemTableEntry current = next;
                next = null;
                return current;
            }

            @Override
            public void close() {
                iterator.close();
            }
        };
    }

    public static InternalIterator snapshotBounded(InternalIterator iterator, long maxSeq) {
        return new FilteringIterator(iterator) {
            @Override
            protected boolean include(MemTableEntry entry) {
                return entry.sequenceNumber() <= maxSeq;
            }
        };
    }

    private abstract static class FilteringIterator implements InternalIterator {

        private final InternalIterator delegate;
        private MemTableEntry next;

        private FilteringIterator(InternalIterator delegate) {
            this.delegate = delegate;
        }

        @Override
        public void seek(byte[] key, long maxSeq) {
            delegate.seek(key, maxSeq);
            next = null;
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            while (delegate.hasNext()) {
                MemTableEntry candidate = delegate.next();
                if (include(candidate)) {
                    next = candidate;
                    return true;
                }
            }
            return false;
        }

        @Override
        public MemTableEntry next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            MemTableEntry current = next;
            next = null;
            return current;
        }

        @Override
        public void close() {
            delegate.close();
        }

        protected abstract boolean include(MemTableEntry entry);
    }
}
