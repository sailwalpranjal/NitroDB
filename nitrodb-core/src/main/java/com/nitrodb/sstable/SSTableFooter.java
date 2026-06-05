package com.nitrodb.sstable;

import com.nitrodb.io.ChecksumUtil;
import java.nio.ByteBuffer;

public record SSTableFooter(
        long bloomFilterOffset,
        int bloomFilterLength,
        long sparseIndexOffset,
        int sparseIndexLength,
        long entryCount,
        long minSeq,
        long maxSeq,
        byte[] minKey,
        byte[] maxKey,
        int crc) {

    public byte[] encode() {
        ByteBuffer payload = ByteBuffer.allocate(
                Long.BYTES + Integer.BYTES + Long.BYTES + Integer.BYTES + Long.BYTES + Long.BYTES + Long.BYTES
                        + Integer.BYTES + minKey.length + Integer.BYTES + maxKey.length + Integer.BYTES);
        payload.putLong(bloomFilterOffset);
        payload.putInt(bloomFilterLength);
        payload.putLong(sparseIndexOffset);
        payload.putInt(sparseIndexLength);
        payload.putLong(entryCount);
        payload.putLong(minSeq);
        payload.putLong(maxSeq);
        payload.putInt(minKey.length);
        payload.put(minKey);
        payload.putInt(maxKey.length);
        payload.put(maxKey);
        int checksum = ChecksumUtil.compute(java.util.Arrays.copyOf(payload.array(), payload.position()));
        payload.putInt(checksum);
        return payload.array();
    }

    public static SSTableFooter decode(ByteBuffer buffer) {
        byte[] payloadWithoutCrc = new byte[buffer.remaining() - Integer.BYTES];
        buffer.mark();
        buffer.get(payloadWithoutCrc);
        int expectedCrc = buffer.getInt();
        ChecksumUtil.validate(payloadWithoutCrc, expectedCrc);
        buffer.reset();
        long bloomOffset = buffer.getLong();
        int bloomLength = buffer.getInt();
        long sparseOffset = buffer.getLong();
        int sparseLength = buffer.getInt();
        long entryCount = buffer.getLong();
        long minSeq = buffer.getLong();
        long maxSeq = buffer.getLong();
        byte[] minKey = new byte[buffer.getInt()];
        buffer.get(minKey);
        byte[] maxKey = new byte[buffer.getInt()];
        buffer.get(maxKey);
        int crc = buffer.getInt();
        return new SSTableFooter(
                bloomOffset,
                bloomLength,
                sparseOffset,
                sparseLength,
                entryCount,
                minSeq,
                maxSeq,
                minKey,
                maxKey,
                crc);
    }
}
