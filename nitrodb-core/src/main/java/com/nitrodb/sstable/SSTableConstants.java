package com.nitrodb.sstable;

public final class SSTableConstants {

    public static final long MAGIC = 0x4E4954524F5353L;
    public static final short VERSION = 1;
    public static final int HEADER_SIZE = 16;
    public static final long FOOTER_MAGIC = 0x4E4954524F464F54L;
    public static final int BLOCK_HEADER_SIZE = Integer.BYTES + Integer.BYTES;
    public static final int MAX_BLOCK_SIZE = 1024 * 1024;

    private SSTableConstants() {
    }
}
