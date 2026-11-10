package com.pedidos360.bff.security;

// App Roles definidos en Microsoft Entra ID (llegan en el claim "roles" del access token)
public final class Roles {

    public static final String ADMIN = "ROLE_ADMINISTRADOR";
    public static final String OPERADOR = "ROLE_OPERADOR";
    public static final String CLIENTE = "ROLE_CLIENTE";

    // Expresiones para @PreAuthorize
    public static final String IS_ADMIN = "hasAuthority('" + ADMIN + "')";
    public static final String IS_STAFF = "hasAnyAuthority('" + ADMIN + "','" + OPERADOR + "')";
    public static final String IS_ANY = "hasAnyAuthority('" + ADMIN + "','" + OPERADOR + "','" + CLIENTE + "')";

    private Roles() {
    }
}
