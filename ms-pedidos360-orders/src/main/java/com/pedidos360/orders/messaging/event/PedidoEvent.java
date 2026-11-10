package com.pedidos360.orders.messaging.event;

import java.time.Instant;

/**
 * Mensaje JSON publicado en pedidos360.pedidos.exchange.
 * tipo = CREADO o CAMBIO_ESTADO; eventId permite a los consumidores descartar duplicados.
 */
public record PedidoEvent(
        String eventId,
        String tipo,
        Long pedidoId,
        String customerId,
        Long productId,
        Integer quantity,
        String estado,
        String estadoAnterior,
        Instant ocurridoEn) {
}
