package com.ksb.springdynamodb.exception;

public class FileAlreadyExistsInStorageException extends RuntimeException {

    public FileAlreadyExistsInStorageException(String key) {
        super("File already exists: " + key);
    }
}
