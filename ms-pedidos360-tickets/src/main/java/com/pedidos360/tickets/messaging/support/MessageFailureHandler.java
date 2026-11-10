package com.pedidos360.tickets.messaging.support;

import com.pedidos360.tickets.config.RabbitProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Decide qué hacer con un mensaje que falló. Política de errores (igual en todos los consumidores):
 *
 * <ul>
 *   <li><b>No recuperable</b> → {@code basicReject(requeue=false)}: RabbitMQ lo envía por el DLX a la DLQ.</li>
 *   <li><b>Recuperable</b> (BD o broker caídos, timeouts) y quedan intentos → se copia a la cola de
 *       reintento con el contador {@value #ATTEMPTS_HEADER} + 1 y se hace {@code basicAck} del original.
 *       Tras el TTL vuelve a la cola principal. No se usa requeue inmediato para no crear bucles.</li>
 *   <li><b>Recuperable sin intentos restantes</b> → {@code basicReject(requeue=false)} → DLQ.</li>
 * </ul>
 * Cada envío a DLQ queda registrado en el log con nivel ERROR.
 */
@Component
public class MessageFailureHandler {

    public static final String ATTEMPTS_HEADER = "x-pedidos360-intentos";

    private static final Logger log = LoggerFactory.getLogger(MessageFailureHandler.class);

    private final RabbitTemplate rabbitTemplate;
    private final int maxAttempts;

    public MessageFailureHandler(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.maxAttempts = props.retry().maxAttempts();
    }

    /** Error no recuperable: rechazo sin reencolar -> DLQ. */
    public void deadLetter(Message message, Channel channel, RabbitProperties.QueueNames queue,
                           Exception error) throws IOException {
        log.error("[DLQ] Mensaje no recuperable en '{}' enviado a '{}'. Motivo: {}. Cuerpo: {}",
                queue.name(), queue.dlq(), error.getMessage(), body(message));
        channel.basicReject(deliveryTag(message), false);
    }

    /** Error recuperable: reintento diferido mientras queden intentos; luego DLQ. */
    public void retryOrDeadLetter(Message message, Channel channel, RabbitProperties.QueueNames queue,
                                  Exception error) throws IOException {
        int attempt = attempts(message) + 1;

        if (attempt >= maxAttempts) {
            log.error("[DLQ] Reintentos agotados ({}/{}) en '{}', enviado a '{}'. Último error: {}. Cuerpo: {}",
                    attempt, maxAttempts, queue.name(), queue.dlq(), error.toString(), body(message));
            channel.basicReject(deliveryTag(message), false);
            return;
        }

        try {
            Message copy = MessageBuilder.fromClonedMessage(message)
                    .setHeader(ATTEMPTS_HEADER, attempt)
                    .build();
            rabbitTemplate.send("", queue.retry(), copy);
            channel.basicAck(deliveryTag(message), false);
            log.warn("[REINTENTO] Intento {}/{} falló en '{}': {}. Se reintentará desde '{}'",
                    attempt, maxAttempts, queue.name(), error.toString(), queue.retry());
        } catch (AmqpException publishError) {
            // Si ni siquiera se puede programar el reintento, el mensaje no se pierde: va a la DLQ
            log.error("[DLQ] No se pudo programar el reintento de '{}' ({}); enviado a '{}'",
                    queue.name(), publishError.getMessage(), queue.dlq());
            channel.basicReject(deliveryTag(message), false);
        }
    }

    public static int attempts(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(ATTEMPTS_HEADER);
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static long deliveryTag(Message message) {
        return message.getMessageProperties().getDeliveryTag();
    }

    private static String body(Message message) {
        String text = new String(message.getBody(), StandardCharsets.UTF_8);
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }
}
