package com.pedidos360.tickets.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración técnica de mensajería del RabbitTemplate (lado productor):
 * <ul>
 *   <li>Los eventos se publican como JSON (ObjectMapper de Spring: fechas ISO-8601).</li>
 *   <li>Se registran en el log los mensajes que el broker no confirma o no puede enrutar (no entregados).</li>
 * </ul>
 * El conversor JSON se aplica solo al RabbitTemplate: los consumidores reciben el mensaje crudo y lo
 * convierten con EventReader, así un JSON inválido se maneja como error no recuperable (DLQ) dentro del
 * propio consumidor, con ACK/NACK explícito.
 */
@Configuration
public class RabbitMessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMessagingConfig.class);

    @Bean
    public RabbitTemplateCustomizer jsonAndDeliveryLoggingCustomizer(ObjectMapper objectMapper) {
        return template -> {
            template.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));

            // Publisher confirm negativo: el broker no aceptó el mensaje
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!ack) {
                    log.error("[NO ENTREGADO] El broker rechazó la publicación id={} causa={}",
                            correlation != null ? correlation.getId() : "-", cause);
                }
            });
            // Mensaje "mandatory" sin cola destino
            template.setReturnsCallback(returned -> log.error(
                    "[NO ENTREGADO] Sin cola destino: exchange={} routingKey={} replyCode={} replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText()));
        };
    }
}
