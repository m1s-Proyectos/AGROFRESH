package com.agrofresh.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.ConnectException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseConnectionVerifierTest {

    @Test
    void verifiesConnectionWithReadOnlySelectOne() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseSchemaAuditor schemaAuditor = mock(DatabaseSchemaAuditor.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        DatabaseConnectionVerifier verifier = new DatabaseConnectionVerifier(jdbcTemplate, schemaAuditor);

        assertThat(verifier.verifyConnection()).isEqualTo(DatabaseConnectionStatus.SUCCESS);
        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
        verifyNoInteractions(schemaAuditor);
    }

    @Test
    void classifiesPostgresAuthenticationFailureWithoutExposingItsMessage() {
        SQLException cause = new SQLException("sensitive database detail", "28P01");

        assertThat(DatabaseConnectionVerifier.classify(
                new DataAccessResourceFailureException("connection failed", cause)))
                .isEqualTo(DatabaseConnectionStatus.INVALID_CREDENTIALS);
    }

    @Test
    void classifiesNetworkAndUnavailableDatabaseErrors() {
        assertThat(DatabaseConnectionVerifier.classify(new ConnectException("host detail")))
                .isEqualTo(DatabaseConnectionStatus.NETWORK_ERROR);
        assertThat(DatabaseConnectionVerifier.classify(new SQLException("database unavailable", "57P03")))
                .isEqualTo(DatabaseConnectionStatus.DATABASE_INACCESSIBLE);
    }

    @Test
    void classifiesUnrecognizedErrorsSeparately() {
        assertThat(DatabaseConnectionVerifier.classify(new IllegalStateException("unexpected")))
                .isEqualTo(DatabaseConnectionStatus.UNEXPECTED_ERROR);
    }
}
