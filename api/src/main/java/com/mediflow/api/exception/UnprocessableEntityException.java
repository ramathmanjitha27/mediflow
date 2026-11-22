package com.mediflow.api.exception;

/**
 * The request body is well-formed but refers to something unusable, e.g. a patient that does not
 * exist or an inactive doctor. Mapped to 422 (unlike 404, the URL itself is fine).
 */
public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String message) {
        super(message);
    }
}
