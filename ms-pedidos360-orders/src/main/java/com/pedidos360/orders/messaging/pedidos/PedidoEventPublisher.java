package com.pedidos360.orders.messaging.pedidos;

import com.pedidos360.orders.config.RabbitProperties;
import com.pedidos360.orders.event.OrderCreatedEvent;
import com.pedidos360.orders.event.OrderStatusChangedEvent;
import com.pedidos360.orders.messaging.event.PedidoEvent;
import com.pedidos360.orders.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Productor: traduce los eventos de dominio de OrderService a mensajes RabbitMQ.
 *
 * Se ejecuta DESPUÉS del commit (AFTER_COMMIT): nunca se anuncia un pedido que no quedó guardado.
 * Si RabbitMQ no está disponible, se registra el error y la operación HTTP igual responde OK:
 * la mensajería es asíncrona y no altera la lógica existente de pedidos.
 */
@Component
public class PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        Order order = event.order();
        publish(props.routingKeys().pedidoCreado(), toMessage("CREADO", order, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        Order order = event.order();
        String routingKey = props.routingKeys().pedidoEstadoPrefijo()
                + order.getStatus().name().toLowerCase(Locale.ROOT);
        publish(routingKey, toMessage("CAMBIO_ESTADO", order, event.previousStatus().name()));
    }

    private void publish(String routingKey, PedidoEvent message) {
        String exchange = props.exchanges().pedidos();
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, new CorrelationData(message.eventId()));
            log.info("Evento publicado: exchange={} routingKey={} pedidoId={} eventId={}",
                    exchange, routingKey, message.pedidoId(), message.eventId());
        } catch (AmqpException e) {
            log.error("[NO ENTREGADO] No se pudo publicar {} del pedido {} en {}: {}",
                    routingKey, message.pedidoId(), exchange, e.getMessage());
        }
    }

    private static PedidoEvent toMessage(String tipo, Order order, String estadoAnterior) {
        return new PedidoEvent(
                UUID.randomUUID().toString(),
                tipo,
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus().name(),
                estadoAnterior,
                Instant.now());
    }
}
