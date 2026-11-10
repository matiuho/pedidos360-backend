package com.pedidos360.orders.controller;

import com.pedidos360.orders.client.CatalogClient.CatalogUnavailableException;
import com.pedidos360.orders.dto.ApiResponseDTO;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.Order.OrderStatus;
import com.pedidos360.orders.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Operaciones relacionadas con los pedidos de Pedidos360")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // =========================================================
    // GET - Obtener todos los pedidos
    // =========================================================

    @GetMapping
    @Operation(summary = "Obtener todos los pedidos")
    public ResponseEntity<ApiResponseDTO<List<Order>>> findAll() {

        List<Order> orders = orderService.findAll();

        return ok(HttpStatus.OK, "Pedidos obtenidos satisfactoriamente", orders, (long) orders.size());
    }

    // =========================================================
    // GET - Obtener un pedido por ID
    // =========================================================

    @GetMapping("/{id}")
    @Operation(summary = "Obtener pedido por ID")
    public ResponseEntity<ApiResponseDTO<Order>> findById(
            @PathVariable Long id) {

        try {
            return ok(HttpStatus.OK, "Pedido encontrado", orderService.findById(id), 1L);

        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // =========================================================
    // GET - Obtener pedidos de un cliente
    // =========================================================

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Obtener los pedidos de un cliente")
    public ResponseEntity<ApiResponseDTO<List<Order>>> findByCustomer(
            @PathVariable String customerId) {

        List<Order> orders = orderService.findByCustomerId(customerId);

        return ok(HttpStatus.OK, "Pedidos del cliente obtenidos satisfactoriamente", orders, (long) orders.size());
    }

    // =========================================================
    // POST - Crear pedido
    // =========================================================

    @PostMapping
    @Operation(summary = "Crear un nuevo pedido")
    public ResponseEntity<ApiResponseDTO<Order>> create(
            @Valid @RequestBody Order order) {

        try {
            return ok(HttpStatus.CREATED, "Pedido creado satisfactoriamente", orderService.create(order), 1L);

        } catch (CatalogUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());

        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "Error creando pedido: " + e.getMessage());
        }
    }

    // =========================================================
    // PUT - Actualizar producto y cantidad de un pedido
    // =========================================================

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un pedido (solo en estado CREADO)")
    public ResponseEntity<ApiResponseDTO<Order>> update(
            @PathVariable Long id,
            @Valid @RequestBody Order orderData) {

        try {
            Order updatedOrder = orderService.update(id, orderData.getProductId(), orderData.getQuantity());

            return ok(HttpStatus.OK, "Pedido actualizado satisfactoriamente", updatedOrder, 1L);

        } catch (CatalogUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());

        } catch (IllegalStateException | IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // =========================================================
    // PUT - Cambiar estado del pedido
    // =========================================================

    @PutMapping("/{id}/status")
    @Operation(summary = "Cambiar el estado de un pedido")
    public ResponseEntity<ApiResponseDTO<Order>> changeStatus(
            @PathVariable Long id,
            @RequestBody StatusRequest request) {

        try {

            if (request.status() == null) {
                throw new IllegalArgumentException(
                        "Debe indicar el nuevo estado del pedido"
                );
            }

            Order updatedOrder =
                    orderService.changeStatus(id, request.status());

            return ok(HttpStatus.OK, "Estado del pedido actualizado satisfactoriamente", updatedOrder, 1L);

        } catch (CatalogUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());

        } catch (IllegalStateException | IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // =========================================================
    // DELETE - Eliminar pedido
    // =========================================================

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un pedido")
    public ResponseEntity<ApiResponseDTO<Void>> delete(
            @PathVariable Long id) {

        try {
            orderService.deleteById(id);

            return ok(HttpStatus.OK, "Pedido eliminado satisfactoriamente", null, 0L);

        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // DTO pequeño para recibir el nuevo estado
    public record StatusRequest(OrderStatus status) {
    }

    // Respuestas en el formato común ApiResponseDTO
    private static <T> ResponseEntity<ApiResponseDTO<T>> ok(
            HttpStatus status, String message, T data, Long count) {

        return ResponseEntity.status(status)
                .body(new ApiResponseDTO<>(true, status.value(), message, data, count));
    }

    private static <T> ResponseEntity<ApiResponseDTO<T>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiResponseDTO<>(false, status.value(), message, null, 0L));
    }
}
