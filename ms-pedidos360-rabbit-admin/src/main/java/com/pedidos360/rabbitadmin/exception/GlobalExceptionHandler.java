package com.pedidos360.rabbitadmin.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// Traduce las excepciones a respuestas JSON (ProblemDetail) con un mensaje claro para el usuario
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // DTO inválido (@Valid @RequestBody)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError field
                        ? field.getField() + ": " + field.getDefaultMessage()
                        : error.getDefaultMessage())
                .toList();
        return badRequest(errors);
    }

    // Path variable o query param inválido (@Pattern en el controlador)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException e) {
        List<String> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + message(error)))
                .toList();
        return badRequest(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException e) {
        return badRequest(List.of(e.getParameterName() + ": es obligatorio"));
    }

    // JSON mal formado o valor de enum inexistente (p. ej. type = "XYZ")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()) {
            String field = invalid.getPath().isEmpty() ? "valor" : invalid.getPath().getLast().getFieldName();
            String allowed = Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(Object::toString).collect(Collectors.joining(", "));
            return badRequest(List.of(field + ": debe ser uno de " + allowed));
        }
        return badRequest(List.of("El cuerpo de la solicitud no es un JSON válido"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail handleConflict(ResourceConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // Broker apagado o inalcanzable
    @ExceptionHandler(AmqpConnectException.class)
    public ProblemDetail handleBrokerDown(AmqpConnectException e) {
        log.error("RabbitMQ no disponible: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "RabbitMQ no está disponible, intente más tarde");
    }

    private static ProblemDetail badRequest(List<String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        problem.setTitle("Datos de entrada inválidos");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String message(MessageSourceResolvable error) {
        return error instanceof DefaultMessageSourceResolvable d && d.getDefaultMessage() != null
                ? d.getDefaultMessage()
                : String.valueOf(error.getDefaultMessage());
    }
}
