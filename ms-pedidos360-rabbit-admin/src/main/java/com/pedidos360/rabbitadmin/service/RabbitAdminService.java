package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.dto.*;
import com.pedidos360.rabbitadmin.exception.ResourceConflictException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Toda la lógica de administración de RabbitMQ (colas, exchanges y bindings).
 * El controlador solo llama a estos métodos de alto nivel y no conoce la API de RabbitMQ.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    // =========================================================
    // Colas
    // =========================================================

    public QueueInfoResponse createQueue(QueueRequest request) {
        if (queueExists(request.name())) {
            throw new ResourceConflictException("La cola '" + request.name() + "' ya existe");
        }
        if (request.deadLetterExchange() != null) {
            requireExchange(request.deadLetterExchange());
        }

        QueueBuilder builder = request.durableOrDefault()
                ? QueueBuilder.durable(request.name())
                : QueueBuilder.nonDurable(request.name());
        if (request.exclusiveOrDefault()) {
            builder.exclusive();
        }
        if (request.autoDeleteOrDefault()) {
            builder.autoDelete();
        }
        if (request.messageTtlMs() != null) {
            builder.ttl(request.messageTtlMs());
        }
        if (request.maxLength() != null) {
            builder.maxLength(request.maxLength());
        }
        if (request.deadLetterExchange() != null) {
            builder.deadLetterExchange(request.deadLetterExchange());
        }
        if (request.deadLetterRoutingKey() != null) {
            builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
        }

        Queue queue = builder.build();
        declare(() -> rabbitAdmin.declareQueue(queue), "la cola '" + queue.getName() + "'");
        log.info("Cola creada: {} (durable={}, args={})", queue.getName(), queue.isDurable(), queue.getArguments());
        return getQueue(queue.getName());
    }

    public QueueInfoResponse getQueue(String name) {
        QueueInformation info = rabbitAdmin.getQueueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        return new QueueInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    public void deleteQueue(String name, boolean ifUnused, boolean ifEmpty) {
        QueueInfoResponse queue = getQueue(name);

        if (ifUnused && queue.consumerCount() > 0) {
            throw new ResourceConflictException("La cola '" + name + "' tiene " + queue.consumerCount()
                    + " consumidor(es) conectado(s)");
        }
        if (ifEmpty && queue.messageCount() > 0) {
            throw new ResourceConflictException("La cola '" + name + "' tiene " + queue.messageCount()
                    + " mensaje(s) pendiente(s)");
        }

        declare(() -> rabbitAdmin.deleteQueue(name, ifUnused, ifEmpty), "la cola '" + name + "'");
        log.info("Cola eliminada: {}", name);
    }

    public PurgeResponse purgeQueue(String name) {
        getQueue(name);
        int purged = rabbitAdmin.purgeQueue(name);
        log.info("Cola vaciada: {} ({} mensajes)", name, purged);
        return new PurgeResponse(name, purged);
    }

    // =========================================================
    // Exchanges
    // =========================================================

    public ExchangeResponse createExchange(ExchangeRequest request) {
        if (exchangeExists(request.name())) {
            throw new ResourceConflictException("El exchange '" + request.name() + "' ya existe");
        }

        Exchange exchange = switch (request.type()) {
            case DIRECT -> new DirectExchange(request.name(), request.durableOrDefault(), request.autoDeleteOrDefault());
            case TOPIC -> new TopicExchange(request.name(), request.durableOrDefault(), request.autoDeleteOrDefault());
            case FANOUT -> new FanoutExchange(request.name(), request.durableOrDefault(), request.autoDeleteOrDefault());
            case HEADERS -> new HeadersExchange(request.name(), request.durableOrDefault(), request.autoDeleteOrDefault());
        };

        declare(() -> rabbitAdmin.declareExchange(exchange), "el exchange '" + exchange.getName() + "'");
        log.info("Exchange creado: {} ({})", exchange.getName(), request.type());
        return new ExchangeResponse(exchange.getName(), request.type(), exchange.isDurable(), exchange.isAutoDelete());
    }

    public void deleteExchange(String name) {
        requireExchange(name);
        declare(() -> rabbitAdmin.deleteExchange(name), "el exchange '" + name + "'");
        log.info("Exchange eliminado: {}", name);
    }

    // =========================================================
    // Bindings
    // =========================================================

    public BindingResponse createBinding(BindingRequest request) {
        requireExchange(request.exchange());
        getQueue(request.queue());

        Binding binding = toBinding(request.exchange(), request.queue(), request.routingKeyOrDefault());
        declare(() -> rabbitAdmin.declareBinding(binding), "el binding");
        log.info("Binding creado: {} -[{}]-> {}", request.exchange(), request.routingKeyOrDefault(), request.queue());
        return new BindingResponse(request.exchange(), request.queue(), request.routingKeyOrDefault());
    }

    public void deleteBinding(String exchange, String queue, String routingKey) {
        requireExchange(exchange);
        getQueue(queue);

        String key = routingKey == null ? "" : routingKey;
        declare(() -> rabbitAdmin.removeBinding(toBinding(exchange, queue, key)), "el binding");
        log.info("Binding eliminado: {} -[{}]-> {}", exchange, key, queue);
    }

    // =========================================================
    // Utilidades internas
    // =========================================================

    private boolean queueExists(String name) {
        return rabbitAdmin.getQueueInfo(name) != null;
    }

    // Declaración pasiva: falla (y RabbitMQ cierra el canal) si el exchange no existe
    private boolean exchangeExists(String name) {
        Boolean exists = rabbitAdmin.getRabbitTemplate().execute(channel -> {
            try {
                channel.exchangeDeclarePassive(name);
                return true;
            } catch (IOException e) {
                return false;
            }
        });
        return Boolean.TRUE.equals(exists);
    }

    private void requireExchange(String name) {
        if (!exchangeExists(name)) {
            throw new ResourceNotFoundException("El exchange '" + name + "' no existe");
        }
    }

    private static Binding toBinding(String exchange, String queue, String routingKey) {
        return new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }

    // Traduce los rechazos del broker (p. ej. PRECONDITION_FAILED) a un 409 con mensaje claro
    private static void declare(Runnable operation, String resource) {
        try {
            operation.run();
        } catch (AmqpIOException e) {
            throw new ResourceConflictException("RabbitMQ rechazó la operación sobre " + resource
                    + ": " + rootMessage(e), e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
