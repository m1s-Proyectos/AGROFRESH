package com.agrofresh.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
public class DatabaseSchemaAuditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseSchemaAuditor.class);
    private static final List<String> EXPECTED_TABLES = List.of(
            "bitacora", "bodega", "categoria", "catalogo", "compra", "detalle_compra",
            "inventario", "lote", "movimiento", "movimiento_bodega", "movimiento_compra",
            "no_perecedero", "perecedero", "producto", "proveedor", "usuario");

    private static final String TABLES_QUERY = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """;

    private static final String COLUMNS_QUERY = """
            SELECT table_name, column_name, data_type, udt_name, is_nullable,
                   column_default, character_maximum_length, numeric_precision,
                   numeric_scale, ordinal_position
            FROM information_schema.columns
            WHERE table_schema = current_schema()
            ORDER BY table_name, ordinal_position
            """;

    private static final String CONSTRAINTS_QUERY = """
            SELECT table_rel.relname AS table_name,
                   con.conname AS constraint_name,
                   CASE con.contype
                       WHEN 'p' THEN 'PRIMARY KEY'
                       WHEN 'f' THEN 'FOREIGN KEY'
                       WHEN 'u' THEN 'UNIQUE'
                       WHEN 'c' THEN 'CHECK'
                       WHEN 'x' THEN 'EXCLUSION'
                       ELSE con.contype::text
                   END AS constraint_type,
                   pg_catalog.pg_get_constraintdef(con.oid, true) AS definition
            FROM pg_catalog.pg_constraint con
            JOIN pg_catalog.pg_class table_rel ON table_rel.oid = con.conrelid
            JOIN pg_catalog.pg_namespace schema_rel ON schema_rel.oid = table_rel.relnamespace
            WHERE schema_rel.nspname = current_schema()
            ORDER BY table_rel.relname, con.conname
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaAuditor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void auditCurrentSchema() {
        List<String> tables = jdbcTemplate.queryForList(TABLES_QUERY, String.class);
        List<String> missing = new ArrayList<>(EXPECTED_TABLES);
        missing.removeAll(tables);
        List<String> unexpected = new ArrayList<>(tables);
        unexpected.removeAll(EXPECTED_TABLES);

        LOGGER.info("Auditoría de esquema PostgreSQL: {} tablas base encontradas; faltantes={}, inesperadas={}",
                tables.size(), missing, unexpected);

        jdbcTemplate.query(COLUMNS_QUERY, (RowCallbackHandler) resultSet -> LOGGER.info(
                "Columna {}.{}: tipo={} ({}), nullable={}, longitud={}, precisión={}, escala={}, default={}",
                resultSet.getString("table_name"), resultSet.getString("column_name"),
                resultSet.getString("data_type"), resultSet.getString("udt_name"),
                resultSet.getString("is_nullable"), resultSet.getObject("character_maximum_length"),
                resultSet.getObject("numeric_precision"), resultSet.getObject("numeric_scale"),
                resultSet.getString("column_default")));

        jdbcTemplate.query(CONSTRAINTS_QUERY, (RowCallbackHandler) resultSet -> LOGGER.info(
                "Restricción {}.{}: tipo={}, definición={}",
                resultSet.getString("table_name"), resultSet.getString("constraint_name"),
                resultSet.getString("constraint_type"), resultSet.getString("definition")));
    }
}
