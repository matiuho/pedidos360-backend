package com.pedidos360.orders.event;

import com.pedidos360.orders.model.Order;

/**
 * Evento de dominio interno (Spring ApplicationEvent): se creó un pedido.
 * OrderService solo publica este evento; no conoce RabbitMQ.
 */
public record OrderCreatedEvent(Order order) {
}
