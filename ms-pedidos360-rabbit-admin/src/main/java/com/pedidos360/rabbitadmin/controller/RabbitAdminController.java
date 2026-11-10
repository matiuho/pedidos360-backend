package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.*;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import static com.pedidos360.rabbitadmin.dto.RabbitNameRules.*;

/**
 * API REST de administración de RabbitMQ (requiere App Role Administrador).
 *
 * <pre>
 * POST   /api/rabbit/queues                     crear cola                  201 / 400 / 404 (DLX) / 409
 * GET    /api/rabbit/queues/{name}              consultar cola              200 / 404
 * DELETE /api/rabbit/queues/{name}              eliminar cola               204 / 404 / 409
 *        ?ifUnused=true&ifEmpty=true            (solo si no tiene consumidores / mensajes)
 * DELETE /api/rabbit/queues/{name}/messages     vaciar cola (purge)         200 / 404
 * POST   /api/rabbit/exchanges                  crear exchange              201 / 400 / 409
 * DELETE /api/rabbit/exchanges/{name}           eliminar exchange           204 / 404
 * POST   /api/rabbit/bindings                   crear binding               201 / 400 / 404
 * DELETE /api/rabbit/bindings?exchange=&queue=&routingKey=   eliminar binding  204 / 400 / 404
 * </pre>
 */
@RestController
@RequestMapping("/api/rabbit")
public class RabbitAdminController {

    private final RabbitAdminService adminService;

    public RabbitAdminController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    // ---------------- Colas ----------------

    @PostMapping("/queues")
    public ResponseEntity<QueueInfoResponse> createQueue(@Valid @RequestBody QueueRequest request,
                                                         UriComponentsBuilder uri) {
        QueueInfoResponse created = adminService.createQueue(request);
        return ResponseEntity.created(uri.path("/api/rabbit/queues/{name}").buildAndExpand(created.name()).toUri())
                .body(created);
    }

    @GetMapping("/queues/{name}")
    public QueueInfoResponse getQueue(
            @PathVariable @Pattern(regexp = EXISTING_NAME, message = EXISTING_NAME_MESSAGE) String name) {
        return adminService.getQueue(name);
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> deleteQueue(
            @PathVariable @Pattern(regexp = NEW_NAME, message = NEW_NAME_MESSAGE) String name,
            @RequestParam(defaultValue = "false") boolean ifUnused,
            @RequestParam(defaultValue = "false") boolean ifEmpty) {
        adminService.deleteQueue(name, ifUnused, ifEmpty);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/queues/{name}/messages")
    public PurgeResponse purgeQueue(
            @PathVariable @Pattern(regexp = EXISTING_NAME, message = EXISTING_NAME_MESSAGE) String name) {
        return adminService.purgeQueue(name);
    }

    // ---------------- Exchanges ----------------

    @PostMapping("/exchanges")
    public ResponseEntity<ExchangeResponse> createExchange(@Valid @RequestBody ExchangeRequest request,
                                                           UriComponentsBuilder uri) {
        ExchangeResponse created = adminService.createExchange(request);
        return ResponseEntity.created(uri.path("/api/rabbit/exchanges/{name}").buildAndExpand(created.name()).toUri())
                .body(created);
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> deleteExchange(
            @PathVariable @Pattern(regexp = NEW_NAME, message = NEW_NAME_MESSAGE) String name) {
        adminService.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Bindings ----------------

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public BindingResponse createBinding(@Valid @RequestBody BindingRequest request) {
        return adminService.createBinding(request);
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> deleteBinding(
            @RequestParam @Pattern(regexp = EXISTING_NAME, message = EXISTING_NAME_MESSAGE) String exchange,
            @RequestParam @Pattern(regexp = EXISTING_NAME, message = EXISTING_NAME_MESSAGE) String queue,
            @RequestParam(defaultValue = "") @Pattern(regexp = ROUTING_KEY, message = ROUTING_KEY_MESSAGE) String routingKey) {
        adminService.deleteBinding(exchange, queue, routingKey);
        return ResponseEntity.noContent().build();
    }
}
