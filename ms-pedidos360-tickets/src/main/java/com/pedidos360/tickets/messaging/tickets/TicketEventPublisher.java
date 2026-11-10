package com.pedidos360.tickets.messaging.tickets;

import com.pedidos360.tickets.config.RabbitProperties;
import com.pedidos360.tickets.messaging.event.TicketGeneradoEvent;
import com.pedidos360.tickets.model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Productor: anuncia que se generó un ticket. Si la publicación falla, la excepción sube al
 * consumidor, que trata el error como recuperable y reintenta (el ticket ya guardado no se duplica).
 */
@Component
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public TicketEventPublisher(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publishGenerated(Ticket ticket) {
        TicketGeneradoEvent event = new TicketGeneradoEvent(
                UUID.randomUUID().toString(),
                ticket.getPedidoId(),
                ticket.getCustomerId(),
                ticket.getCodigo(),
                Instant.now());

        rabbitTemplate.convertAndSend(props.exchanges().tickets(), props.routingKeys().ticketGenerado(),
                event, new CorrelationData(event.eventId()));

        log.info("Evento publicado: exchange={} routingKey={} pedidoId={} ticket={}",
                props.exchanges().tickets(), props.routingKeys().ticketGenerado(),
                ticket.getPedidoId(), ticket.getCodigo());
    }
}
