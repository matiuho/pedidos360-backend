package com.pedidos360.tickets.messaging.event;

import java.time.Instant;

/** Mensaje publicado en pedidos360.tickets.exchange con routing key ticket.generado. */
public record TicketGeneradoEvent(
        String eventId,
        Long pedidoId,
        String customerId,
        String codigoTicket,
        Instant generadoEn) {
}
