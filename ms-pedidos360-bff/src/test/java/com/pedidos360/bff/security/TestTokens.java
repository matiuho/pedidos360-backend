package com.pedidos360.bff.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

// Genera JWT firmados como los de Entra ID, para probar la validación real del BFF
public final class TestTokens {

    public static final String ISSUER = "https://login.microsoftonline.com/test-tenant/v2.0";
    public static final String AUDIENCE = "api-client-id";
    public static final List<String> AUDIENCES = List.of(AUDIENCE, "api://pedidos360-api");

    public static final KeyPair KEYS = generateKeys();
    public static final KeyPair OTHER_KEYS = generateKeys();

    private TestTokens() {
    }

    public static JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        decoder.setJwtValidator(JwtConfig.tokenValidator(ISSUER, AUDIENCES));
        return decoder;
    }

    public static String token(String oid, String... roles) {
        return builder(oid, roles).sign();
    }

    public static Builder builder(String oid, String... roles) {
        return new Builder(oid, List.of(roles));
    }

    public static final class Builder {
        private final String oid;
        private final List<String> roles;
        private String issuer = ISSUER;
        private String audience = AUDIENCE;
        private Instant expiresAt = Instant.now().plusSeconds(3600);
        private KeyPair keys = KEYS;

        private Builder(String oid, List<String> roles) {
            this.oid = oid;
            this.roles = roles;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder signedWith(KeyPair keys) {
            this.keys = keys;
            return this;
        }

        public String sign() {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject("sub-" + oid)
                    .claim("oid", oid)
                    .claim("roles", roles)
                    .claim("scp", "Pedidos.ReadWrite")
                    .issueTime(Date.from(expiresAt.minusSeconds(7200)))
                    .expirationTime(Date.from(expiresAt))
                    .build();

            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
                jwt.sign(new RSASSASigner(keys.getPrivate()));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // Reemplaza el decoder real (que descarga las llaves de Entra ID) por uno con la llave de prueba
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public JwtDecoder testJwtDecoder() {
            return decoder();
        }
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
