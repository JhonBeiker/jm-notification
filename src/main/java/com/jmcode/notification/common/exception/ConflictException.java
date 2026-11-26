package com.jmcode.notification.common.exception;

/** Estado actual incompatible con la operación (409): p. ej. borrar una empresa con datos asociados. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
