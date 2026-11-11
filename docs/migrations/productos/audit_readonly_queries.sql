-- Etapa 10.6 — consultas de solo lectura (metadatos y agregados).
-- No ejecutar escrituras. No incluir en salida filas identificables.

\set ON_ERROR_STOP on
SET search_path TO inventario, public;

-- Sesión
SELECT current_database() AS db, current_user AS db_user, current_schema() AS current_schema;
SHOW search_path;

-- Tablas requeridas
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'inventario'
  AND table_name = ANY (ARRAY[
    'producto','perecedero','no_perecedero','categoria',
    'lote','movimiento','inventario','detalle_compra','catalogo'])
ORDER BY table_name;

-- Columnas (producto y subtipos)
SELECT table_name, column_name, data_type, udt_name, is_nullable,
       character_maximum_length, numeric_precision, numeric_scale,
       is_identity, column_default
FROM information_schema.columns
WHERE table_schema = 'inventario'
  AND table_name IN ('producto','perecedero','no_perecedero','categoria')
ORDER BY table_name, ordinal_position;

-- PK / FK / UNIQUE / CHECK
SELECT c.relname AS table_name, con.conname AS constraint_name,
       CASE con.contype
           WHEN 'p' THEN 'PRIMARY KEY'
           WHEN 'f' THEN 'FOREIGN KEY'
           WHEN 'u' THEN 'UNIQUE'
           WHEN 'c' THEN 'CHECK'
           ELSE con.contype::text
       END AS constraint_type,
       pg_get_constraintdef(con.oid, true) AS definition
FROM pg_constraint con
JOIN pg_class c ON c.oid = con.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'inventario'
  AND c.relname IN ('producto','perecedero','no_perecedero','categoria',
                    'lote','movimiento','inventario','detalle_compra','catalogo')
ORDER BY c.relname, con.conname;

-- Índices
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'inventario'
  AND tablename IN ('producto','perecedero','no_perecedero','categoria',
                    'lote','movimiento','inventario','detalle_compra','catalogo')
ORDER BY tablename, indexname;

-- Secuencias / identidad (producto)
SELECT pg_get_serial_sequence('inventario.producto', 'id_producto') AS producto_id_seq;

-- EXISTS (mismo SQL que ProductoRepository) con id inexistente
SELECT
    (SELECT EXISTS(SELECT 1 FROM lote WHERE id_producto = -1)) AS exists_lote,
    (SELECT EXISTS(SELECT 1 FROM movimiento WHERE id_producto = -1)) AS exists_movimiento,
    (SELECT EXISTS(SELECT 1 FROM inventario WHERE id_producto = -1)) AS exists_inventario_tabla,
    (SELECT EXISTS(SELECT 1 FROM detalle_compra WHERE id_producto = -1)) AS exists_detalle_compra,
    (SELECT EXISTS(SELECT 1 FROM catalogo WHERE id_producto = -1)) AS exists_catalogo;

-- EXISTS calificados (control)
SELECT
    (SELECT EXISTS(SELECT 1 FROM inventario.lote WHERE id_producto = -1)) AS q_lote,
    (SELECT EXISTS(SELECT 1 FROM inventario.inventario WHERE id_producto = -1)) AS q_inventario;

-- Integridad clasificación (solo conteos)
SELECT COUNT(*) AS cnt_producto FROM inventario.producto;
SELECT COUNT(*) AS cnt_perecedero FROM inventario.perecedero;
SELECT COUNT(*) AS cnt_no_perecedero FROM inventario.no_perecedero;

SELECT COUNT(*) AS productos_sin_clasificacion
FROM inventario.producto p
WHERE NOT EXISTS (SELECT 1 FROM inventario.perecedero pe WHERE pe.id_producto = p.id_producto)
  AND NOT EXISTS (SELECT 1 FROM inventario.no_perecedero np WHERE np.id_producto = p.id_producto);

SELECT COUNT(*) AS productos_con_ambas_clasificaciones
FROM inventario.producto p
WHERE EXISTS (SELECT 1 FROM inventario.perecedero pe WHERE pe.id_producto = p.id_producto)
  AND EXISTS (SELECT 1 FROM inventario.no_perecedero np WHERE np.id_producto = p.id_producto);

SELECT COUNT(*) AS perecedero_huerfano
FROM inventario.perecedero pe
WHERE NOT EXISTS (SELECT 1 FROM inventario.producto p WHERE p.id_producto = pe.id_producto);

SELECT COUNT(*) AS no_perecedero_huerfano
FROM inventario.no_perecedero np
WHERE NOT EXISTS (SELECT 1 FROM inventario.producto p WHERE p.id_producto = np.id_producto);

SELECT COUNT(*) AS producto_categoria_inexistente
FROM inventario.producto p
WHERE NOT EXISTS (SELECT 1 FROM inventario.categoria c WHERE c.id_categoria = p.id_categoria);

-- Dependencias (conteos de productos distintos referenciados, sin listar ids)
SELECT COUNT(DISTINCT id_producto) AS productos_con_lote
FROM inventario.lote WHERE id_producto IS NOT NULL;

SELECT COUNT(DISTINCT id_producto) AS productos_con_movimiento
FROM inventario.movimiento WHERE id_producto IS NOT NULL;

SELECT COUNT(DISTINCT id_producto) AS productos_en_inventario
FROM inventario.inventario WHERE id_producto IS NOT NULL;

SELECT COUNT(DISTINCT id_producto) AS productos_en_detalle_compra
FROM inventario.detalle_compra WHERE id_producto IS NOT NULL;

SELECT COUNT(DISTINCT id_producto) AS productos_en_catalogo
FROM inventario.catalogo WHERE id_producto IS NOT NULL;
