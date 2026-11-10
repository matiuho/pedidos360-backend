package com.pedidos360.tickets.repository;

import com.pedidos360.tickets.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByPedidoId(Long pedidoId);
}
