package com.agrofresh.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "agrofresh.datasource-check", name = "enabled", havingValue = "true")
public class DatabaseConnectionVerifier implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConnectionVerifier.class);
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSchemaAuditor schemaAuditor;

    public DatabaseConnectionVerifier(JdbcTemplate jdbcTemplate, DatabaseSchemaAuditor schemaAuditor) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaAuditor = schemaAuditor;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatabaseConnectionStatus status = verifyConnection();
        switch (status) {
            case SUCCESS -> {
                LOGGER.info("Conexión PostgreSQL verificada mediante SELECT 1.");
                try {
                    schemaAuditor.auditCurrentSchema();
                } catch (DataAccessException exception) {
                    LOGGER.error("La conexión funciona, pero la auditoría de catálogo PostgreSQL no pudo completarse.");
                }
            }
            case INVALID_CREDENTIALS -> LOGGER.error(
                    "PostgreSQL rechazó las credenciales; revisa DB_USER y DB_PASSWORD.");
            case NETWORK_ERROR -> LOGGER.error(
                    "No fue posible alcanzar PostgreSQL por un error de red; revisa host, puerto y conectividad.");
            case DATABASE_INACCESSIBLE -> LOGGER.error(
                    "La instancia PostgreSQL no está disponible o rechazó la conexión.");
            case UNEXPECTED_ERROR -> LOGGER.error(
                    "La comprobación de PostgreSQL falló por un error no clasificado.");
        }
    }

    DatabaseConnectionStatus verifyConnection() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result)
                    ? DatabaseConnectionStatus.SUCCESS
                    : DatabaseConnectionStatus.UNEXPECTED_ERROR;
        } catch (DataAccessException exception) {
            return classify(exception);
        }
    }

    static DatabaseConnectionStatus classify(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null) {
                    if (sqlState.startsWith("28")) {
                        return DatabaseConnectionStatus.INVALID_CREDENTIALS;
                    }
                    if (sqlState.startsWith("08")) {
                        return DatabaseConnectionStatus.NETWORK_ERROR;
                    }
                    if (sqlState.startsWith("3D") || sqlState.startsWith("3F")
                            || sqlState.startsWith("53") || sqlState.startsWith("57")) {
                        return DatabaseConnectionStatus.DATABASE_INACCESSIBLE;
                    }
                }
            }

            if (cause instanceof UnknownHostException || cause instanceof ConnectException
                    || cause instanceof SocketException || cause instanceof SocketTimeoutException) {
                return DatabaseConnectionStatus.NETWORK_ERROR;
            }
        }

        if (hasCauseType(failure, SQLException.class)) {
            return DatabaseConnectionStatus.DATABASE_INACCESSIBLE;
        }
        return DatabaseConnectionStatus.UNEXPECTED_ERROR;
    }

    private static boolean hasCauseType(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
