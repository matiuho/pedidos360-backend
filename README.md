# Pedidos360 · Backend (EP3 – Mensajería con RabbitMQ)

Backend de **Pedidos360** construido con microservicios **Java 21 + Spring Boot 3.5**. En esta etapa se incorporó **RabbitMQ** para resolver de forma **asíncrona** la generación de comprobantes (tickets) y el envío de notificaciones, **sin modificar las reglas de negocio existentes** de pedidos y catálogo.

Frontend (repositorio separado): `pedidos360-frontend` (Angular + Microsoft Entra ID).

---

## 1. Microservicios

| Microservicio | Puerto | Rol | RabbitMQ |
|---|---:|---|---|
| `ms-pedidos360-bff` | 8080 | Puerta de entrada del frontend. **Valida el JWT de Microsoft Entra ID** y aplica permisos por rol | — |
| `ms-pedidos360-catalog` | 8081 | Productos y stock (MySQL) | — |
| `ms-pedidos360-orders` | 8082 | Pedidos y su ciclo de estados (MySQL) | **Productor** `pedido.*` · **Consumidor** `ticket.generado` |
| `ms-pedidos360-notifications` | 8083 | Registra y "envía" (simulado) notificaciones al cliente (MySQL) | **Consumidor** `pedido.#` y `ticket.generado` |
| `ms-pedidos360-rabbit-admin` | 8084 | **Administrador de RabbitMQ**: crea/elimina colas, exchanges y bindings vía REST. Valida el JWT (rol Administrador) | RabbitAdmin |
| `ms-pedidos360-tickets` | 8085 | Genera el comprobante de cada pedido aceptado (MySQL) | **Consumidor** `pedido.estado.aceptado` · **Productor** `ticket.generado` |

Infraestructura: **MySQL 8** (una base por microservicio) y **RabbitMQ 4.1** con consola web.

## 2. Flujo asíncrono

```
 Angular ──JWT──► BFF ──► ms-orders ──(commit en MySQL)──► [pedidos360.pedidos.exchange] (topic)
                                                              │ pedido.creado
                                                              │ pedido.estado.<estado>
                     ┌────────────────────────────────────────┤
                     │ pedido.#                               │ pedido.estado.aceptado
                     ▼                                        ▼
          (notifications.pedidos)                   (tickets.pedidos-aceptados)
          ms-notifications                          ms-tickets ── guarda comprobante
                     ▲                                        │
                     │ ticket.generado                        ▼
          (notifications.tickets) ◄──── [pedidos360.tickets.exchange] (topic)
                                                              │ ticket.generado
                                                              ▼
                                               (orders.tickets-generados)
                                               ms-orders ── guarda ticketCode en el pedido

 Cualquier mensaje rechazado ──► [pedidos360.dlx] (direct) ──► <cola>.dlq
```

1. El cliente crea un pedido → `ms-orders` lo guarda y **después del commit** publica `pedido.creado`.
2. El operador lo acepta → se descuenta el stock (regla original, síncrona) y se publica `pedido.estado.aceptado`.
3. `ms-tickets` genera el comprobante (`TCK-yyyyMMdd-000123`) y publica `ticket.generado`.
4. `ms-orders` guarda el código en el pedido y `ms-notifications` avisa al cliente.

Si RabbitMQ está caído, **crear o aceptar pedidos sigue funcionando**: la publicación falla, se registra en el log y la respuesta HTTP no cambia.

## 3. Topología RabbitMQ

Todos los nombres están **centralizados en el `application.yml`** de cada microservicio (bloque `pedidos360.rabbitmq`) y se leen con un `@ConfigurationProperties` (`RabbitProperties`). Ninguna clase de negocio escribe nombres de colas a mano.

| Exchange | Tipo | Publica | Routing keys |
|---|---|---|---|
| `pedidos360.pedidos.exchange` | topic | ms-orders | `pedido.creado`, `pedido.estado.aceptado`, `pedido.estado.despachado`, … |
| `pedidos360.tickets.exchange` | topic | ms-tickets | `ticket.generado` |
| `pedidos360.dlx` | direct | RabbitMQ (dead-lettering) | nombre de la DLQ |

| Cola de trabajo | Binding | Consumidor | DLQ | Cola de reintento |
|---|---|---|---|---|
| `pedidos360.tickets.pedidos-aceptados` | `pedido.estado.aceptado` | `PedidoAceptadoListener` | `….dlq` | `….retry` |
| `pedidos360.notifications.pedidos` | `pedido.#` | `PedidoNotificationListener` | `….dlq` | `….retry` |
| `pedidos360.notifications.tickets` | `ticket.generado` | `TicketNotificationListener` | `….dlq` | `….retry` |
| `pedidos360.orders.tickets-generados` | `ticket.generado` | `TicketGeneradoListener` | `….dlq` | `….retry` |

Cada ruta se declara con **beans `Queue`, `Exchange` y `Binding`** en la clase `config/RabbitTopologyConfig` de cada servicio, documentada con un diagrama en el Javadoc.

## 4. Confirmación de mensajes y manejo de errores

Los consumidores usan `@RabbitListener` con **ACK manual** (`acknowledge-mode: manual`) y están **agrupados por dominio** (`messaging/pedidos`, `messaging/tickets`). Cada uno sigue el mismo patrón explícito:

```java
try {
    Evento e = reader.read(message, Evento.class);   // JSON + Bean Validation
    servicio.procesar(e);
    channel.basicAck(tag, false);                    // 1) éxito -> ACK
} catch (NonRecoverableMessageException | <errores de datos> e) {
    failures.deadLetter(...);                        // 2) basicReject(requeue=false) -> DLQ
} catch (Exception e) {
    failures.retryOrDeadLetter(...);                 // 3) reintento diferido o DLQ
}
```

| Situación | Ejemplo | Decisión |
|---|---|---|
| Procesado correctamente | — | `basicAck` |
| **No recuperable** | JSON inválido, campo obligatorio ausente, pedido inexistente, estado inválido | `basicReject(requeue=false)` → **DLQ** (no se reintenta: fallaría igual) |
| **Recuperable** con intentos disponibles | MySQL caído, RabbitMQ caído al publicar, timeout | Copia a la cola `….retry` (TTL 5 s) con el header `x-pedidos360-intentos` + 1 y `basicAck` del original. Al vencer el TTL vuelve a la cola principal |
| Recuperable **sin intentos** (3 en total) | La BD sigue caída | `basicReject(requeue=false)` → **DLQ** |

* No se usa `requeue=true`: evita bucles infinitos que saturan al consumidor.
* **Todo envío a DLQ queda en el log** con nivel `ERROR` y el prefijo `[DLQ]` (cola, motivo y cuerpo).
* Los productores usan **publisher confirms** y **mandatory/returns**: un mensaje que el broker rechaza o que no llega a ninguna cola se registra como `[NO ENTREGADO]`.
* Los consumidores son **idempotentes** (`eventId` único en notificaciones y `pedido_id` único en tickets), así que un mensaje duplicado no genera efectos dobles.

## 5. Microservicio administrador de RabbitMQ

`ms-pedidos360-rabbit-admin` expone una API REST protegida con JWT (App Role **Administrador**). El controlador solo llama a `RabbitAdminService`, que concentra toda la lógica con `RabbitAdmin`.

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/api/rabbit/queues` | Crear cola (durable, TTL, máx. mensajes, DLX) | 201 · 400 · 404 (DLX inexistente) · 409 (ya existe) |
| `GET` | `/api/rabbit/queues/{name}` | Mensajes y consumidores de una cola | 200 · 404 |
| `DELETE` | `/api/rabbit/queues/{name}?ifUnused=&ifEmpty=` | Eliminar cola | 204 · 404 · 409 |
| `DELETE` | `/api/rabbit/queues/{name}/messages` | Vaciar cola (purge) | 200 · 404 |
| `POST` | `/api/rabbit/exchanges` | Crear exchange (`DIRECT`, `TOPIC`, `FANOUT`, `HEADERS`) | 201 · 400 · 409 |
| `DELETE` | `/api/rabbit/exchanges/{name}` | Eliminar exchange | 204 · 404 |
| `POST` | `/api/rabbit/bindings` | Crear binding exchange → cola | 201 · 400 · 404 |
| `DELETE` | `/api/rabbit/bindings?exchange=&queue=&routingKey=` | Eliminar binding | 204 · 400 · 404 |

**Validación de entrada** (DTO `record` con Bean Validation + `@Pattern` en path/query params):

* Nombre obligatorio, sin espacios, 1-255 caracteres `[A-Za-z0-9._:-]`, sin el prefijo reservado `amq.`.
* `messageTtlMs` y `maxLength` positivos y con máximo; `type` debe ser un tipo de exchange válido.
* `deadLetterRoutingKey` exige `deadLetterExchange`; una cola `exclusive` no puede ser `durable`.
* Los errores se devuelven como `ProblemDetail` con la lista `errors`, por ejemplo:

```json
{ "status": 400, "title": "Datos de entrada inválidos",
  "detail": "name: debe tener 1-255 caracteres (letras, números, . _ - :), …",
  "errors": ["name: debe tener 1-255 caracteres …"] }
```

## 6. Seguridad (IDaaS)

* **Microsoft Entra ID** emite el JWT. El BFF y rabbit-admin lo validan como *OAuth2 Resource Server* (firma JWKS, `exp`/`nbf`, `issuer` y `audience`).
* Roles (App Roles del claim `roles`): `ROLE_ADMINISTRADOR`, `ROLE_OPERADOR`, `ROLE_CLIENTE`.
* catalog, orders, tickets y notifications solo son accesibles desde la red interna de Docker (no publican puertos).

## 7. Cómo ejecutar

Requisitos: JDK 21, Maven 3.9+, Docker Desktop.

```bash
cp .env.example .env              # completar claves y datos de Entra ID
mvn clean verify                  # compila y prueba los 6 microservicios
docker compose up -d --build      # MySQL + RabbitMQ + microservicios
```

* Consola RabbitMQ: <http://localhost:15672> (usuario/clave de `.env`, por defecto `pedidos360`/`pedidos360`).
* BFF: <http://localhost:8080> · rabbit-admin: <http://localhost:8084>.
* Para desarrollar desde el IDE: `docker compose up -d mysql rabbitmq` y ejecutar cada servicio (usan `localhost` por defecto).

## 8. Pruebas

```bash
mvn clean verify                                   # pruebas unitarias y de integración (H2, sin broker)
cd ms-pedidos360-rabbit-admin
RABBIT_IT=true mvn test -Dtest=RabbitAdminServiceIT # contra un RabbitMQ real (docker compose up -d rabbitmq)
```

| Servicio | Qué se prueba |
|---|---|
| orders | Reglas de estados originales, publicación de `pedido.creado` / `pedido.estado.aceptado`, broker caído no bloquea el pedido, ACK / DLQ / reintento del consumidor |
| tickets | Generación idempotente del ticket, rechazo de estados inválidos, ACK y reintento si falla la publicación |
| notifications | Registro por cliente, idempotencia por `eventId`, ACK / DLQ / reintento de ambos consumidores |
| rabbit-admin | 401/403 por JWT y rol, validaciones (nombre vacío, caracteres, `amq.`, TTL, tipo inválido, routing key), códigos 201/204/404/409 |
| bff | Validación del JWT (firma, issuer, audience, expiración) y permisos por rol |

### Demostración de DLQ y reintentos

1. **Mensaje no recuperable**: en la consola RabbitMQ → *Queues* → `pedidos360.notifications.pedidos` → *Publish message* con el cuerpo `{esto no es json}`. Termina en `pedidos360.notifications.pedidos.dlq` y el log muestra `[DLQ] Mensaje no recuperable…`.
2. **Mensaje recuperable**: `docker compose stop mysql`, aceptar un pedido, observar `[REINTENTO] Intento 1/3…` en el log de tickets o notifications y luego `docker compose start mysql`: el siguiente intento lo procesa. Si la BD no vuelve, al tercer intento va a la DLQ.

## 9. Mejoras propuestas

* *Transactional Outbox* para garantizar la publicación aunque el broker esté caído justo después del commit.
* Colas *quorum* y réplicas de RabbitMQ para alta disponibilidad.
* Endpoint en rabbit-admin para reprocesar mensajes desde la DLQ.
