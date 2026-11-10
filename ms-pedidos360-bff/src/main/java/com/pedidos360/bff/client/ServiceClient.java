package com.pedidos360.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.InputStream;

// Llamada HTTP a un microservicio que devuelve su código de estado y su JSON sin modificar
public abstract class ServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    protected ServiceClient(String baseUrl, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    protected ResponseEntity<JsonNode> call(HttpMethod method, String uri, Object body, Object... uriVariables) {
        RestClient.RequestBodySpec request = restClient.method(method)
                .uri(uri, uriVariables)
                .accept(MediaType.APPLICATION_JSON);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return request.exchange((req, res) -> {
            try (InputStream in = res.getBody()) {
                byte[] bytes = in.readAllBytes();
                JsonNode json = bytes.length == 0 ? null : objectMapper.readTree(bytes);
                return ResponseEntity.status(res.getStatusCode()).body(json);
            }
        });
    }
}
