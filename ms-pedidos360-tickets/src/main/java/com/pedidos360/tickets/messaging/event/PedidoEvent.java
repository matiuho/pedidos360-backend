package com.pedidos360.tickets.messaging.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Evento de pedido publicado por ms-pedidos360-orders (solo los campos que usa este servicio). */
public record PedidoEvent(
        @NotBlank String eventId,
        String tipo,
        @NotNull Long pedidoId,
        @NotBlank String customerId,
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        @NotBlank String estado,
        String estadoAnterior,
        Instant ocurridoEn) {
}
