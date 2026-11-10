package com.pedidos360.rabbitadmin.dto;

/**
 * Reglas de nombres para colas, exchanges y routing keys (se usan en las anotaciones de validación).
 */
public final class RabbitNameRules {

    /** Nombre nuevo: letras, números, punto, guion, guion bajo o dos puntos; no puede empezar con "amq." (reservado). */
    public static final String NEW_NAME = "^(?!amq\\.)[A-Za-z0-9][A-Za-z0-9._:-]{0,254}$";
    public static final String NEW_NAME_MESSAGE =
            "debe tener 1-255 caracteres (letras, números, . _ - :), empezar con letra o número y no usar el prefijo reservado 'amq.'";

    /** Referencia a un recurso existente (permite los exchanges del sistema amq.*). */
    public static final String EXISTING_NAME = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,254}$";
    public static final String EXISTING_NAME_MESSAGE =
            "debe tener 1-255 caracteres (letras, números, . _ - :) y empezar con letra o número";

    /** Routing key / patrón: palabras separadas por punto; admite comodines * y # de los topic exchanges. */
    public static final String ROUTING_KEY = "^[A-Za-z0-9._:*#-]{0,255}$";
    public static final String ROUTING_KEY_MESSAGE =
            "solo admite letras, números, . _ - : y los comodines * # (máximo 255 caracteres)";

    private RabbitNameRules() {
    }
}
