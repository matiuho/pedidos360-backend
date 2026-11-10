package com.pedidos360.notifications.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de ms-pedidos360-notifications.
 *
 * <pre>
 * Ruta 1 (consumidor) - notificar eventos de pedido
 *   [pedidos360.pedidos.exchange] --pedido.#--> (pedidos360.notifications.pedidos)
 *     --> PedidoNotificationListener
 *
 * Ruta 2 (consumidor) - notificar ticket disponible
 *   [pedidos360.tickets.exchange] --ticket.generado--> (pedidos360.notifications.tickets)
 *     --> TicketNotificationListener
 *
 * Cada cola: error recuperable --> (….retry, TTL) --> cola principal;
 *            no recuperable o reintentos agotados --> [pedidos360.dlx] --> (….dlq)
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitTopologyConfig {

    private final RabbitProperties props;

    public RabbitTopologyConfig(RabbitProperties props) {
        this.props = props;
    }

    // ---------------- Exchanges ----------------

    @Bean
    public TopicExchange pedidosExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().pedidos()).durable(true).build();
    }

    @Bean
    public TopicExchange ticketsExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().tickets()).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }

    // ---------------- Ruta 1: eventos de pedido ----------------

    @Bean
    public Queue pedidosNotificationQueue() {
        return workQueue(props.queues().pedidos());
    }

    @Bean
    public Queue pedidosNotificationDlq() {
        return QueueBuilder.durable(props.queues().pedidos().dlq()).build();
    }

    @Bean
    public Queue pedidosNotificationRetryQueue() {
        return retryQueue(props.queues().pedidos());
    }

    @Bean
    public Binding pedidosNotificationBinding(@Qualifier("pedidosNotificationQueue") Queue queue,
                                              @Qualifier("pedidosExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(props.routingKeys().pedidosTodos());
    }

    @Bean
    public Binding pedidosNotificationDlqBinding(@Qualifier("pedidosNotificationDlq") Queue dlq,
                                                 @Qualifier("deadLetterExchange") DirectExchange dlx) {
        return BindingBuilder.bind(dlq).to(dlx).with(props.queues().pedidos().dlq());
    }

    // ---------------- Ruta 2: tickets generados ----------------

    @Bean
    public Queue ticketsNotificationQueue() {
        return workQueue(props.queues().tickets());
    }

    @Bean
    public Queue ticketsNotificationDlq() {
        return QueueBuilder.durable(props.queues().tickets().dlq()).build();
    }

    @Bean
    public Queue ticketsNotificationRetryQueue() {
        return retryQueue(props.queues().tickets());
    }

    @Bean
    public Binding ticketsNotificationBinding(@Qualifier("ticketsNotificationQueue") Queue queue,
                                              @Qualifier("ticketsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(props.routingKeys().ticketGenerado());
    }

    @Bean
    public Binding ticketsNotificationDlqBinding(@Qualifier("ticketsNotificationDlq") Queue dlq,
                                                 @Qualifier("deadLetterExchange") DirectExchange dlx) {
        return BindingBuilder.bind(dlq).to(dlx).with(props.queues().tickets().dlq());
    }

    // ---------------- Plantillas ----------------

    /** Cola de trabajo cuyos mensajes rechazados van al DLX con la DLQ como routing key. */
    private Queue workQueue(RabbitProperties.QueueNames q) {
        return QueueBuilder.durable(q.name())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(q.dlq())
                .build();
    }

    /** Cola de reintento: retiene el mensaje retry.delay-ms y lo devuelve a la cola principal. */
    private Queue retryQueue(RabbitProperties.QueueNames q) {
        return QueueBuilder.durable(q.retry())
                .ttl((int) props.retry().delayMs())
                .deadLetterExchange("")
                .deadLetterRoutingKey(q.name())
                .build();
    }
}
