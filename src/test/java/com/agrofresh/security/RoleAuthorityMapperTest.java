package com.agrofresh.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAuthorityMapperTest {

    @Test
    void mapsOnlyTheTwoExistingDatabaseRolesToApplicationAuthorities() {
        assertEquals("ROLE_ADMIN", RoleAuthorityMapper.authorityFor("ADMINISTRADOR").orElseThrow());
        assertEquals("ROLE_OPERADOR", RoleAuthorityMapper.authorityFor("OPERATIVO").orElseThrow());
        assertFalse(RoleAuthorityMapper.authorityFor("OTRO").isPresent());
        assertFalse(RoleAuthorityMapper.authorityFor(null).isPresent());
    }

    @Test
    void recognizesOnlyTheAllowedApplicationAuthorities() {
        assertTrue(RoleAuthorityMapper.isAllowedAuthority("ROLE_ADMIN"));
        assertTrue(RoleAuthorityMapper.isAllowedAuthority("ROLE_OPERADOR"));
        assertFalse(RoleAuthorityMapper.isAllowedAuthority("ROLE_ADMINISTRADOR"));
        assertFalse(RoleAuthorityMapper.isAllowedAuthority("ROLE_OTRO"));
    }

    @Test
    void bcryptEncoderMatchesOnlyTheGeneratedUnitTestFixture() {
        var encoder = new BCryptPasswordEncoder();
        String fixtureHash = encoder.encode("unit-test-only-password");

        assertTrue(encoder.matches("unit-test-only-password", fixtureHash));
        assertFalse(encoder.matches("different-unit-test-password", fixtureHash));
    }
}
