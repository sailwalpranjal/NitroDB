package com.nitrodb.wal;

public final class WalConstants {

    public static final long MAGIC = 0x4E4954524F574CL;
    public static final short VERSION = 1;
    public static final int HEADER_SIZE = 16;
    public static final int FRAME_HEADER_SIZE = Integer.BYTES + Integer.BYTES;

    private WalConstants() {
    }
}
