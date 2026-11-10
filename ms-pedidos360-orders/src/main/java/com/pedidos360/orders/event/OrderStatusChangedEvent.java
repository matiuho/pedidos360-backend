package com.pedidos360.orders.event;

import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.Order.OrderStatus;

/**
 * Evento de dominio interno: un pedido cambió de estado.
 */
public record OrderStatusChangedEvent(Order order, OrderStatus previousStatus) {
}
