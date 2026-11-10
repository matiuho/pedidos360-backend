package com.pedidos360.orders.exception;

// Pedido inexistente. Extiende RuntimeException: el controlador lo sigue respondiendo como 404.
public class OrderNotFoundException extends RuntimeException {

    private final Long orderId;

    public OrderNotFoundException(Long orderId) {
        super("Pedido no encontrado");
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
