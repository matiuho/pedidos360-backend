package com.pedidos360.bff.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

// Datos del usuario autenticado leídos desde los claims del JWT
public record CurrentUser(String id, boolean staff) {

    public static CurrentUser from(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();

        // "oid" identifica al usuario en el tenant; "sub" como respaldo
        String id = jwt.getClaimAsString("oid") != null ? jwt.getClaimAsString("oid") : jwt.getSubject();

        boolean staff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(Roles.ADMIN) || a.equals(Roles.OPERADOR));

        return new CurrentUser(id, staff);
    }
}
