package com.agrofresh.security;

import java.util.Optional;

/** Maps the existing database role names to the application's closed authority set. */
public final class RoleAuthorityMapper {

    private RoleAuthorityMapper() {
    }

    public static Optional<String> authorityFor(String databaseRole) {
        if (databaseRole == null) {
            return Optional.empty();
        }
        return switch (databaseRole) {
            case "ADMINISTRADOR" -> Optional.of("ROLE_ADMIN");
            case "OPERATIVO" -> Optional.of("ROLE_OPERADOR");
            default -> Optional.empty();
        };
    }

    public static boolean isAllowedAuthority(String authority) {
        return "ROLE_ADMIN".equals(authority) || "ROLE_OPERADOR".equals(authority);
    }
}
