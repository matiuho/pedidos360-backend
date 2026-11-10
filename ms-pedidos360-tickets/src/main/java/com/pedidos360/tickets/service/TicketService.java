package com.pedidos360.tickets.service;

import com.pedidos360.tickets.model.Ticket;
import com.pedidos360.tickets.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// Lógica de negocio: generar el comprobante de un pedido aceptado. No conoce RabbitMQ.
@Service
@Transactional
public class TicketService {

    private static final String ESTADO_ACEPTADO = "ACEPTADO";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Genera el ticket del pedido. Es idempotente: si el mensaje llega repetido devuelve el ticket existente.
     *
     * @throws IllegalArgumentException si el pedido no está ACEPTADO (dato inválido, no se reintenta)
     */
    public Ticket generate(Long pedidoId, String customerId, Long productId, Integer quantity, String estado) {
        if (!ESTADO_ACEPTADO.equals(estado)) {
            throw new IllegalArgumentException(
                    "Solo se generan tickets de pedidos ACEPTADO (estado recibido: " + estado + ")");
        }

        Optional<Ticket> existing = ticketRepository.findByPedidoId(pedidoId);
        if (existing.isPresent()) {
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        String codigo = "TCK-" + now.format(DAY) + "-" + String.format("%06d", pedidoId);

        String contenido = String.join("\n",
                "================================",
                "      COMPROBANTE PEDIDOS360",
                "================================",
                "Ticket   : " + codigo,
                "Pedido   : #" + pedidoId,
                "Cliente  : " + customerId,
                "Producto : " + productId,
                "Cantidad : " + quantity,
                "Estado   : " + estado,
                "Emitido  : " + now.format(DATE_TIME),
                "================================");

        return ticketRepository.save(new Ticket(codigo, pedidoId, customerId, productId, quantity, contenido, now));
    }

    @Transactional(readOnly = true)
    public List<Ticket> findAll() {
        return ticketRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> findByPedidoId(Long pedidoId) {
        return ticketRepository.findByPedidoId(pedidoId);
    }
}
