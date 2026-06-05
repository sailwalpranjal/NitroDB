package com.nitrodb.api;

public class DBException extends RuntimeException {

    public DBException(String message) {
        super(message);
    }

    public DBException(String message, Throwable cause) {
        super(message, cause);
    }

    public static final class CorruptionException extends DBException {
        public CorruptionException(String message) {
            super(message);
        }

        public CorruptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class IOStorageException extends DBException {
        public IOStorageException(String message) {
            super(message);
        }

        public IOStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class DatabaseLockException extends DBException {
        public DatabaseLockException(String message) {
            super(message);
        }

        public DatabaseLockException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class RecoveryException extends DBException {
        public RecoveryException(String message) {
            super(message);
        }

        public RecoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
