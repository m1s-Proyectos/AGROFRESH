-- MIG-10.6-OPT-01 — search_path para consultas nativas sin prefijo de esquema
-- Estado: PROPUESTO — NO EJECUTADO
-- Objetivo: Garantizar que consultas como `FROM lote` resuelvan `inventario.lote`.
-- Justificación: ProductoRepository usa EXISTS sin calificar esquema; fallan si search_path no incluye `inventario`.
-- Riesgo: Bajo en el entorno actual (sesión ya reporta `inventario, public`); medio si el rol cambia en otro despliegue.
-- Impacto: Solo configuración del rol de aplicación; no altera tablas.

-- ========== Verificaciones previas (solo lectura) ==========
SHOW search_path;
SELECT current_schema();
SELECT rolname, rolconfig
FROM pg_roles
WHERE rolname = current_user;

-- ========== Cambio propuesto (ejecutar solo tras autorización) ==========
-- ALTER ROLE CURRENT_USER SET search_path = inventario, public;

-- ========== Verificación posterior ==========
-- Reconectar y repetir SHOW search_path;
-- Debe mostrar: inventario, public

-- ========== Reversión ==========
-- ALTER ROLE CURRENT_USER RESET search_path;
