package com.pedidos360.catalog.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long id, int available, int requested) {
        super("Stock insuficiente para el producto " + id
                + ": disponible " + available + ", solicitado " + requested);
    }
}
