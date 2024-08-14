package info.cleanflow.storage.jdbc;

public class StorageException extends RuntimeException{

    protected StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    protected StorageException(String message) {
        super(message);
    }

}
