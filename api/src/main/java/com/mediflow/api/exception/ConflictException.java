package com.mediflow.api.exception;

/** The request is valid but clashes with existing data, e.g. a duplicate email. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
