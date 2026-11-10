package com.pedidos360.orders.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

// Cliente HTTP hacia ms-pedidos360-catalog para validar productos y coordinar stock
@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(@Value("${catalog.service.url}") String catalogServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogServiceUrl + "/api/catalog/products")
                .build();
    }

    // Verifica que el producto exista en el catálogo
    public void assertProductExists(Long productId) {
        try {
            restClient.get()
                    .uri("/{id}", productId)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("El producto " + productId + " no existe en el catálogo");
        } catch (ResourceAccessException e) {
            throw new CatalogUnavailableException(e);
        }
    }

    // Descuenta stock del producto (regla: el stock disminuye al aceptar el pedido)
    public void decreaseStock(Long productId, int quantity) {
        try {
            restClient.put()
                    .uri("/{id}/stock/decrease", productId)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new IllegalStateException("Stock insuficiente para aceptar el pedido");
            }
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new IllegalStateException("El producto " + productId + " ya no existe en el catálogo");
            }
            throw e;
        } catch (ResourceAccessException e) {
            throw new CatalogUnavailableException(e);
        }
    }

    public static class CatalogUnavailableException extends RuntimeException {

        public CatalogUnavailableException(Throwable cause) {
            super("El servicio de catálogo no está disponible", cause);
        }
    }
}
