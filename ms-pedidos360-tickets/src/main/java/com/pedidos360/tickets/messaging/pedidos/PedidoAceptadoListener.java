package com.pedidos360.tickets.messaging.pedidos;

import com.pedidos360.tickets.config.RabbitProperties;
import com.pedidos360.tickets.messaging.event.PedidoEvent;
import com.pedidos360.tickets.messaging.support.EventReader;
import com.pedidos360.tickets.messaging.support.MessageFailureHandler;
import com.pedidos360.tickets.messaging.support.NonRecoverableMessageException;
import com.pedidos360.tickets.messaging.tickets.TicketEventPublisher;
import com.pedidos360.tickets.model.Ticket;
import com.pedidos360.tickets.service.TicketService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio "pedidos": por cada pedido ACEPTADO genera su ticket y publica ticket.generado.
 *
 * ACK manual: basicAck solo después de guardar el ticket Y publicar el evento.
 */
@Component
public class PedidoAceptadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoAceptadoListener.class);

    private final EventReader reader;
    private final TicketService ticketService;
    private final TicketEventPublisher publisher;
    private final MessageFailureHandler failures;
    private final RabbitProperties.QueueNames queue;

    public PedidoAceptadoListener(EventReader reader, TicketService ticketService, TicketEventPublisher publisher,
                                  MessageFailureHandler failures, RabbitProperties props) {
        this.reader = reader;
        this.ticketService = ticketService;
        this.publisher = publisher;
        this.failures = failures;
        this.queue = props.queues().pedidosAceptados();
    }

    @RabbitListener(id = "pedidosAceptados", queues = "${pedidos360.rabbitmq.queues.pedidos-aceptados.name}")
    public void onPedidoAceptado(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            PedidoEvent event = reader.read(message, PedidoEvent.class);
            Ticket ticket = ticketService.generate(event.pedidoId(), event.customerId(),
                    event.productId(), event.quantity(), event.estado());
            publisher.publishGenerated(ticket);

            channel.basicAck(deliveryTag, false);                       // 1) Procesado -> ACK
            log.info("Ticket {} generado para el pedido {}", ticket.getCodigo(), event.pedidoId());

        } catch (NonRecoverableMessageException | IllegalArgumentException e) {
            failures.deadLetter(message, channel, queue, e);             // 2) No recuperable -> DLQ

        } catch (Exception e) {
            failures.retryOrDeadLetter(message, channel, queue, e);      // 3) Recuperable -> reintento/DLQ
        }
    }
}
