package com.pedidos360.tickets.messaging.support;

/**
 * Error que no se arregla reintentando (JSON inválido, campos obligatorios ausentes, datos que no
 * existen). El mensaje se rechaza sin reencolar y RabbitMQ lo envía directo a la DLQ.
 */
public class NonRecoverableMessageException extends RuntimeException {

    public NonRecoverableMessageException(String message) {
        super(message);
    }

    public NonRecoverableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
