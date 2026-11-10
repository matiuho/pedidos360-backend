package com.pedidos360.tickets.config;

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
 * Topología RabbitMQ de ms-pedidos360-tickets.
 *
 * <pre>
 * Ruta 1 (consumidor) - generar ticket de pedido aceptado
 *   [pedidos360.pedidos.exchange] --pedido.estado.aceptado--> (pedidos360.tickets.pedidos-aceptados)
 *     --> PedidoAceptadoListener --> TicketService (guarda el comprobante en MySQL)
 *   Error recuperable    --> (….retry) con TTL --> vuelve a la cola principal
 *   No recuperable o reintentos agotados --> [pedidos360.dlx] --> (….dlq)
 *
 * Ruta 2 (productor) - ticket generado
 *   TicketEventPublisher --> [pedidos360.tickets.exchange] (topic) routing key: ticket.generado
 *     (lo consumen ms-orders y ms-notifications)
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

    /** Exchange de eventos de pedido (lo publica ms-orders; aquí solo se enlaza). */
    @Bean
    public TopicExchange pedidosExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().pedidos()).durable(true).build();
    }

    /** Exchange donde este servicio publica los tickets generados. */
    @Bean
    public TopicExchange ticketsExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().tickets()).durable(true).build();
    }

    /** Dead Letter Exchange compartido. */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }

    // ---------------- Ruta 1: pedidos aceptados ----------------

    @Bean
    public Queue pedidosAceptadosQueue() {
        RabbitProperties.QueueNames q = props.queues().pedidosAceptados();
        return QueueBuilder.durable(q.name())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(q.dlq())
                .build();
    }

    @Bean
    public Queue pedidosAceptadosDlq() {
        return QueueBuilder.durable(props.queues().pedidosAceptados().dlq()).build();
    }

    /** Retiene el mensaje retry.delay-ms y lo devuelve a la cola principal por el exchange por defecto. */
    @Bean
    public Queue pedidosAceptadosRetryQueue() {
        RabbitProperties.QueueNames q = props.queues().pedidosAceptados();
        return QueueBuilder.durable(q.retry())
                .ttl((int) props.retry().delayMs())
                .deadLetterExchange("")
                .deadLetterRoutingKey(q.name())
                .build();
    }

    @Bean
    public Binding pedidosAceptadosBinding(@Qualifier("pedidosAceptadosQueue") Queue queue,
                                           @Qualifier("pedidosExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(props.routingKeys().pedidoAceptado());
    }

    @Bean
    public Binding pedidosAceptadosDlqBinding(@Qualifier("pedidosAceptadosDlq") Queue dlq,
                                              @Qualifier("deadLetterExchange") DirectExchange dlx) {
        return BindingBuilder.bind(dlq).to(dlx).with(props.queues().pedidosAceptados().dlq());
    }
}
