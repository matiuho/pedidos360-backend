package com.pedidos360.notifications.messaging.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Evento de pedido publicado por ms-pedidos360-orders. tipo = CREADO o CAMBIO_ESTADO. */
public record PedidoEvent(
        @NotBlank String eventId,
        @NotBlank String tipo,
        @NotNull Long pedidoId,
        @NotBlank String customerId,
        Long productId,
        Integer quantity,
        @NotBlank String estado,
        String estadoAnterior,
        Instant ocurridoEn) {
}
