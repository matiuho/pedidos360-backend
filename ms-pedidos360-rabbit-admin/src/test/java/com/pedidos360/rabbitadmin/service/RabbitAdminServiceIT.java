package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.dto.*;
import com.pedidos360.rabbitadmin.exception.ResourceConflictException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import com.pedidos360.rabbitadmin.security.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba contra un RabbitMQ real (docker compose up -d rabbitmq).
 * Se ejecuta solo con la variable RABBIT_IT=true:  RABBIT_IT=true mvn test
 */
@SpringBootTest
@Import(TestTokens.Config.class)
@EnabledIfEnvironmentVariable(named = "RABBIT_IT", matches = "true")
class RabbitAdminServiceIT {

    private static final String QUEUE = "it.demo.cola";
    private static final String EXCHANGE = "it.demo.exchange";

    @Autowired
    private RabbitAdminService service;

    @AfterEach
    void cleanUp() {
        try { service.deleteQueue(QUEUE, false, false); } catch (ResourceNotFoundException ignored) { }
        try { service.deleteExchange(EXCHANGE); } catch (ResourceNotFoundException ignored) { }
    }

    @Test
    void cicloCompletoColaExchangeBinding() {
        service.createExchange(new ExchangeRequest(EXCHANGE, ExchangeType.TOPIC, true, false));
        QueueInfoResponse queue = service.createQueue(
                new QueueRequest(QUEUE, true, false, false, 60_000, 100, EXCHANGE, "muertos"));
        assertThat(queue.messageCount()).isZero();

        service.createBinding(new BindingRequest(EXCHANGE, QUEUE, "pedido.#"));
        service.deleteBinding(EXCHANGE, QUEUE, "pedido.#");

        service.deleteQueue(QUEUE, true, true);
        assertThatThrownBy(() -> service.getQueue(QUEUE)).isInstanceOf(ResourceNotFoundException.class);

        service.deleteExchange(EXCHANGE);
        assertThatThrownBy(() -> service.deleteExchange(EXCHANGE)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void colaDuplicadaDevuelveConflicto() {
        service.createQueue(new QueueRequest(QUEUE, true, false, false, null, null, null, null));

        assertThatThrownBy(() -> service.createQueue(new QueueRequest(QUEUE, true, false, false, null, null, null, null)))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void bindingConExchangeInexistenteDevuelve404() {
        service.createQueue(new QueueRequest(QUEUE, true, false, false, null, null, null, null));

        assertThatThrownBy(() -> service.createBinding(new BindingRequest("no.existe", QUEUE, "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dlxInexistenteDevuelve404() {
        assertThatThrownBy(() -> service.createQueue(
                new QueueRequest(QUEUE, true, false, false, null, null, "no.existe", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
