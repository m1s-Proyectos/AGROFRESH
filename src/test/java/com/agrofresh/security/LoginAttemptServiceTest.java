package com.agrofresh.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LoginAttemptServiceTest {

    @Test
    void recordsFailureAndKeepsTheOriginalThreeAttemptThreshold() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LoginAttemptService service = new LoginAttemptService(jdbc);

        service.registrarFallo("unit-test-user");

        verify(jdbc).update(contains("intentos_fallidos + 1 >= 3"), eq("unit-test-user"));
    }

    @Test
    void ignoresBlankUsernamesWithoutDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LoginAttemptService service = new LoginAttemptService(jdbc);

        service.registrarFallo(" ");
        service.registrarFallo(null);

        verifyNoInteractions(jdbc);
    }
}
