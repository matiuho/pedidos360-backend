package com.pedidos360.notifications.messaging.tickets;

import com.pedidos360.notifications.config.RabbitProperties;
import com.pedidos360.notifications.messaging.event.TicketGeneradoEvent;
import com.pedidos360.notifications.messaging.support.EventReader;
import com.pedidos360.notifications.messaging.support.MessageFailureHandler;
import com.pedidos360.notifications.messaging.support.NonRecoverableMessageException;
import com.pedidos360.notifications.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio "tickets": avisa al cliente que su comprobante está disponible.
 */
@Component
public class TicketNotificationListener {

    private final EventReader reader;
    private final NotificationService notificationService;
    private final MessageFailureHandler failures;
    private final RabbitProperties.QueueNames queue;

    public TicketNotificationListener(EventReader reader, NotificationService notificationService,
                                      MessageFailureHandler failures, RabbitProperties props) {
        this.reader = reader;
        this.notificationService = notificationService;
        this.failures = failures;
        this.queue = props.queues().tickets();
    }

    @RabbitListener(id = "notificacionesTickets", queues = "${pedidos360.rabbitmq.queues.tickets.name}")
    public void onTicketGenerado(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            TicketGeneradoEvent event = reader.read(message, TicketGeneradoEvent.class);
            notificationService.notifyTicketGenerated(
                    event.eventId(), event.customerId(), event.pedidoId(), event.codigoTicket());

            channel.basicAck(deliveryTag, false);                       // 1) Procesado -> ACK

        } catch (NonRecoverableMessageException e) {
            failures.deadLetter(message, channel, queue, e);             // 2) No recuperable -> DLQ

        } catch (Exception e) {
            failures.retryOrDeadLetter(message, channel, queue, e);      // 3) Recuperable -> reintento/DLQ
        }
    }
}
