package com.pedidos360.rabbitadmin.exception;

// La operación choca con el estado actual del broker (ya existe, está en uso, etc.) -> HTTP 409
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
