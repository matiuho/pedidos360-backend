package com.pedidos360.orders.messaging.tickets;

import com.pedidos360.orders.config.RabbitProperties;
import com.pedidos360.orders.exception.OrderNotFoundException;
import com.pedidos360.orders.messaging.event.TicketGeneradoEvent;
import com.pedidos360.orders.messaging.support.EventReader;
import com.pedidos360.orders.messaging.support.MessageFailureHandler;
import com.pedidos360.orders.messaging.support.NonRecoverableMessageException;
import com.pedidos360.orders.service.OrderService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio "tickets": cuando ms-tickets genera el comprobante de un pedido aceptado,
 * ms-orders guarda el código del ticket en el pedido.
 *
 * ACK manual: basicAck al terminar bien; los errores se delegan a MessageFailureHandler.
 */
@Component
public class TicketGeneradoListener {

    private static final Logger log = LoggerFactory.getLogger(TicketGeneradoListener.class);

    private final EventReader reader;
    private final OrderService orderService;
    private final MessageFailureHandler failures;
    private final RabbitProperties.QueueNames queue;

    public TicketGeneradoListener(EventReader reader, OrderService orderService,
                                  MessageFailureHandler failures, RabbitProperties props) {
        this.reader = reader;
        this.orderService = orderService;
        this.failures = failures;
        this.queue = props.queues().ticketsGenerados();
    }

    @RabbitListener(id = "ticketsGenerados", queues = "${pedidos360.rabbitmq.queues.tickets-generados.name}")
    public void onTicketGenerado(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            TicketGeneradoEvent event = reader.read(message, TicketGeneradoEvent.class);
            orderService.assignTicket(event.pedidoId(), event.codigoTicket());

            channel.basicAck(deliveryTag, false);                       // 1) Procesado -> ACK
            log.info("Ticket {} asignado al pedido {}", event.codigoTicket(), event.pedidoId());

        } catch (NonRecoverableMessageException | OrderNotFoundException e) {
            failures.deadLetter(message, channel, queue, e);             // 2) No recuperable -> DLQ

        } catch (Exception e) {
            failures.retryOrDeadLetter(message, channel, queue, e);      // 3) Recuperable -> reintento/DLQ
        }
    }
}
