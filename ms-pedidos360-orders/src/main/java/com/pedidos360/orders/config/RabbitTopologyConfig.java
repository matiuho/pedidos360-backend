package com.pedidos360.orders.config;

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
 * Topología RabbitMQ de ms-pedidos360-orders. RabbitAdmin la declara en el broker al conectarse.
 *
 * <pre>
 * Ruta 1 (productor) - ciclo de vida del pedido
 *   OrderService --(evento de dominio)--> PedidoEventPublisher
 *     --> [pedidos360.pedidos.exchange] (topic)  routing keys: pedido.creado | pedido.estado.&lt;estado&gt;
 *
 * Ruta 2 (consumidor) - ticket generado
 *   [pedidos360.tickets.exchange] --ticket.generado--> (pedidos360.orders.tickets-generados)
 *     --> TicketGeneradoListener: guarda el código del ticket en el pedido
 *   Error recuperable    --> (….retry) con TTL --> vuelve a la cola principal
 *   Error no recuperable o reintentos agotados --> [pedidos360.dlx] --> (….dlq)
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

    /** Exchange donde ms-orders publica los eventos de pedido. */
    @Bean
    public TopicExchange pedidosExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().pedidos()).durable(true).build();
    }

    /** Exchange donde ms-tickets publica los tickets generados (ms-orders solo se enlaza a él). */
    @Bean
    public TopicExchange ticketsExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().tickets()).durable(true).build();
    }

    /** Dead Letter Exchange compartido: enruta cada mensaje rechazado a la DLQ de su cola. */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }

    // ---------------- Ruta 2: ticket generado ----------------

    /** Cola de trabajo: al rechazarse un mensaje (basicReject sin requeue) RabbitMQ lo envía al DLX. */
    @Bean
    public Queue ticketsGeneradosQueue() {
        RabbitProperties.QueueNames q = props.queues().ticketsGenerados();
        return QueueBuilder.durable(q.name())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(q.dlq())
                .build();
    }

    /** DLQ: guarda los mensajes que no se pudieron procesar para revisarlos después. */
    @Bean
    public Queue ticketsGeneradosDlq() {
        return QueueBuilder.durable(props.queues().ticketsGenerados().dlq()).build();
    }

    /**
     * Cola de reintento: retiene el mensaje durante retry.delay-ms y luego lo devuelve a la cola
     * principal mediante el exchange por defecto ("") usando el nombre de la cola como routing key.
     */
    @Bean
    public Queue ticketsGeneradosRetryQueue() {
        RabbitProperties.QueueNames q = props.queues().ticketsGenerados();
        return QueueBuilder.durable(q.retry())
                .ttl((int) props.retry().delayMs())
                .deadLetterExchange("")
                .deadLetterRoutingKey(q.name())
                .build();
    }

    @Bean
    public Binding ticketsGeneradosBinding(@Qualifier("ticketsGeneradosQueue") Queue queue,
                                           @Qualifier("ticketsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(props.routingKeys().ticketGenerado());
    }

    @Bean
    public Binding ticketsGeneradosDlqBinding(@Qualifier("ticketsGeneradosDlq") Queue dlq,
                                              @Qualifier("deadLetterExchange") DirectExchange dlx) {
        return BindingBuilder.bind(dlq).to(dlx).with(props.queues().ticketsGenerados().dlq());
    }
}
