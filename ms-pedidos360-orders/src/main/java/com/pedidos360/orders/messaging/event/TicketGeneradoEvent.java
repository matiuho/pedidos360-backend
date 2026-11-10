package com.pedidos360.orders.messaging.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Mensaje JSON que publica ms-pedidos360-tickets al generar el comprobante de un pedido aceptado.
 */
public record TicketGeneradoEvent(
        @NotBlank String eventId,
        @NotNull Long pedidoId,
        String customerId,
        @NotBlank String codigoTicket,
        Instant generadoEn) {
}
