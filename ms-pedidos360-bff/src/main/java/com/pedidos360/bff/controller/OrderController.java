package com.pedidos360.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pedidos360.bff.client.OrderClient;
import com.pedidos360.bff.security.CurrentUser;
import com.pedidos360.bff.security.Roles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final String CANCELADO = "CANCELADO";
    private static final String CREADO = "CREADO";

    private final OrderClient orderClient;

    public OrderController(OrderClient orderClient) {
        this.orderClient = orderClient;
    }

    // Admin/Operador ven todos los pedidos; Cliente solo los suyos
    @GetMapping
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> getOrders(Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);

        return user.staff()
                ? orderClient.getOrders()
                : orderClient.getOrdersByCustomer(user.id());
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> getOrder(@PathVariable Long id, Authentication authentication) {
        ResponseEntity<JsonNode> order = orderClient.getOrder(id);
        assertCanAccess(order, CurrentUser.from(authentication));
        return order;
    }

    // Crear pedido: todos los roles. El dueño siempre es el usuario del token.
    @PostMapping
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> createOrder(@RequestBody ObjectNode order, Authentication authentication) {
        order.put("customerId", CurrentUser.from(authentication).id());
        return orderClient.createOrder(order);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> updateOrder(@PathVariable Long id, @RequestBody ObjectNode order,
                                                Authentication authentication) {

        ResponseEntity<JsonNode> current = orderClient.getOrder(id);
        if (!isFound(current)) {
            return current;
        }

        assertCanAccess(current, CurrentUser.from(authentication));

        // El dueño del pedido no se puede cambiar
        order.put("customerId", data(current).path("customerId").asText());
        return orderClient.updateOrder(id, order);
    }

    // Cambiar estado: Admin/Operador. El Cliente solo puede CANCELAR su pedido mientras está CREADO.
    @PutMapping("/{id}/status")
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> changeStatus(@PathVariable Long id, @RequestBody JsonNode request,
                                                 Authentication authentication) {

        String status = request.path("status").asText(null);
        CurrentUser user = CurrentUser.from(authentication);

        if (!user.staff()) {
            ResponseEntity<JsonNode> current = orderClient.getOrder(id);
            if (!isFound(current)) {
                return current;
            }

            assertCanAccess(current, user);

            boolean cancelOwnCreated = CANCELADO.equals(status)
                    && CREADO.equals(data(current).path("status").asText());

            if (!cancelOwnCreated) {
                throw new AccessDeniedException("El cliente solo puede cancelar pedidos en estado CREADO");
            }
        }

        return orderClient.changeStatus(id, status);
    }

    // Eliminar pedido: solo Admin
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<JsonNode> deleteOrder(@PathVariable Long id) {
        return orderClient.deleteOrder(id);
    }

    // El Cliente solo puede acceder a sus propios pedidos
    private static void assertCanAccess(ResponseEntity<JsonNode> order, CurrentUser user) {
        if (user.staff() || !isFound(order)) {
            return;
        }

        String ownerId = data(order).path("customerId").asText();
        if (!ownerId.equals(user.id())) {
            throw new AccessDeniedException("El pedido pertenece a otro cliente");
        }
    }

    private static boolean isFound(ResponseEntity<JsonNode> response) {
        return response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
    }

    private static JsonNode data(ResponseEntity<JsonNode> response) {
        return response.getBody().path("data");
    }
}
