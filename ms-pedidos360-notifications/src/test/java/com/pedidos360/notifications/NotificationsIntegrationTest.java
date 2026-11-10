package com.pedidos360.notifications;

import com.pedidos360.notifications.model.Notification.NotificationType;
import com.pedidos360.notifications.repository.NotificationRepository;
import com.pedidos360.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationsIntegrationTest {

    @Autowired
    private NotificationService service;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void registraNotificacionesDelCliente() throws Exception {
        service.notifyOrderCreated("e-1", "oid-a", 1L);
        service.notifyOrderStatus("e-2", "oid-a", 1L, "ACEPTADO");
        service.notifyTicketGenerated("e-3", "oid-a", 1L, "TCK-1");
        service.notifyOrderCreated("e-4", "oid-b", 2L);

        mockMvc.perform(get("/api/notifications/customer/oid-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        assertThat(repository.findAll())
                .extracting(n -> n.getTipo())
                .contains(NotificationType.PEDIDO_CREADO, NotificationType.PEDIDO_ESTADO, NotificationType.TICKET_GENERADO);
    }

    @Test
    void eventoDuplicadoNoSeNotificaDosVeces() {
        service.notifyOrderCreated("e-1", "oid-a", 1L);
        service.notifyOrderCreated("e-1", "oid-a", 1L);

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void estadoDesconocidoEsDatoInvalido() {
        assertThatThrownBy(() -> service.notifyOrderStatus("e-9", "oid-a", 1L, "INVENTADO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
