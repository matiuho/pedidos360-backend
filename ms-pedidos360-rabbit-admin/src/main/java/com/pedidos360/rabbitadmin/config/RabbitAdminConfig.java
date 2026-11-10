package com.pedidos360.rabbitadmin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de mensajería del administrador: RabbitAdmin ejecuta las declaraciones y eliminaciones
 * en el broker. Este microservicio no declara colas propias: las crea y elimina bajo demanda vía REST.
 */
@Configuration
public class RabbitAdminConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // Los errores de declaración se propagan para responder 409/503 en vez de ocultarlos
        admin.setIgnoreDeclarationExceptions(false);
        return admin;
    }
}
