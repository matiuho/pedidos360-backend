package com.pedidos360.notifications.messaging.pedidos;

import com.pedidos360.notifications.config.RabbitProperties;
import com.pedidos360.notifications.messaging.event.PedidoEvent;
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
 * Consumidor del dominio "pedidos": notifica al cliente la creación y cada cambio de estado de su pedido.
 */
@Component
public class PedidoNotificationListener {

    private static final String CREADO = "CREADO";
    private static final String CAMBIO_ESTADO = "CAMBIO_ESTADO";

    private final EventReader reader;
    private final NotificationService notificationService;
    private final MessageFailureHandler failures;
    private final RabbitProperties.QueueNames queue;

    public PedidoNotificationListener(EventReader reader, NotificationService notificationService,
                                      MessageFailureHandler failures, RabbitProperties props) {
        this.reader = reader;
        this.notificationService = notificationService;
        this.failures = failures;
        this.queue = props.queues().pedidos();
    }

    @RabbitListener(id = "notificacionesPedidos", queues = "${pedidos360.rabbitmq.queues.pedidos.name}")
    public void onPedidoEvent(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            PedidoEvent event = reader.read(message, PedidoEvent.class);

            switch (event.tipo()) {
                case CREADO -> notificationService.notifyOrderCreated(
                        event.eventId(), event.customerId(), event.pedidoId());
                case CAMBIO_ESTADO -> notificationService.notifyOrderStatus(
                        event.eventId(), event.customerId(), event.pedidoId(), event.estado());
                default -> throw new NonRecoverableMessageException("Tipo de evento desconocido: " + event.tipo());
            }

            channel.basicAck(deliveryTag, false);                       // 1) Procesado -> ACK

        } catch (NonRecoverableMessageException | IllegalArgumentException e) {
            failures.deadLetter(message, channel, queue, e);             // 2) No recuperable -> DLQ

        } catch (Exception e) {
            failures.retryOrDeadLetter(message, channel, queue, e);      // 3) Recuperable -> reintento/DLQ
        }
    }
}
