package com.pedidos360.bff.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

// Segunda validación del JWT emitido por Microsoft Entra ID (la primera la hace API Gateway)
@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${entra.jwk-set-uri}") String jwkSetUri,
            @Value("${entra.issuer}") String issuer,
            @Value("${entra.audiences}") List<String> audiences) {

        // Firma: se verifica con las llaves públicas publicadas por Entra ID (JWKS)
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(tokenValidator(issuer, audiences));
        return decoder;
    }

    // Vigencia (exp/nbf) + issuer + audience
    public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, List<String> audiences) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(audiences)
        );
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> audiences) {
        OAuth2Error error = new OAuth2Error("invalid_token", "El token no fue emitido para esta API (audience)", null);

        return jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
