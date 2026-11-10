package com.pedidos360.rabbitadmin.exception;

// El recurso de RabbitMQ (cola o exchange) no existe -> HTTP 404
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
