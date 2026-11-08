package com.ksb.springdynamodb.exception;

public class InvalidFileRequestException extends RuntimeException {

    public InvalidFileRequestException(String message) {
        super(message);
    }
}
