package com.picmeup.common.exception;

/** A request that clashes with work already in progress, rather than one that is malformed. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
