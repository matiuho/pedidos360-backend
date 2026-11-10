package com.pedidos360.tickets.service;

import com.pedidos360.tickets.model.Ticket;
import com.pedidos360.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TicketServiceTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    void generaTicketDePedidoAceptado() {
        Ticket ticket = ticketService.generate(15L, "oid-1", 3L, 2, "ACEPTADO");

        assertThat(ticket.getCodigo()).startsWith("TCK-").endsWith("-000015");
        assertThat(ticket.getContenido()).contains("Pedido   : #15").contains("Cantidad : 2");
    }

    @Test
    void esIdempotenteSiElMensajeLlegaRepetido() {
        Ticket first = ticketService.generate(15L, "oid-1", 3L, 2, "ACEPTADO");
        Ticket second = ticketService.generate(15L, "oid-1", 3L, 2, "ACEPTADO");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(ticketRepository.count()).isEqualTo(1);
    }

    @Test
    void rechazaPedidoQueNoEstaAceptado() {
        assertThatThrownBy(() -> ticketService.generate(15L, "oid-1", 3L, 2, "CREADO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
