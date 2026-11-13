-- MIG-10.6-OPT-02 — exclusividad de clasificación (defensa en profundidad)
-- Estado: PROPUESTO — NO EJECUTADO
-- Objetivo: Impedir que un mismo id_producto exista en perecedero y no_perecedero a la vez.
-- Justificación: No hay restricción declarativa actual; la aplicación valida en servicio. Datos actuales: 0 conflictos.
-- Riesgo: Medio si se intenta INSERT manual en ambas tablas; bajo con uso solo vía aplicación.
-- Impacto: Triggers en inventario.perecedero e inventario.no_perecedero; no modifica datos históricos automáticamente.

-- ========== Verificaciones previas (solo lectura) ==========
SELECT COUNT(*) AS productos_con_ambas
FROM inventario.producto p
WHERE EXISTS (SELECT 1 FROM inventario.perecedero pe WHERE pe.id_producto = p.id_producto)
  AND EXISTS (SELECT 1 FROM inventario.no_perecedero np WHERE np.id_producto = p.id_producto);

SELECT COUNT(*) AS productos_sin_clasificacion
FROM inventario.producto p
WHERE NOT EXISTS (SELECT 1 FROM inventario.perecedero pe WHERE pe.id_producto = p.id_producto)
  AND NOT EXISTS (SELECT 1 FROM inventario.no_perecedero np WHERE np.id_producto = p.id_producto);

-- Abortar migración si cualquier conteo > 0 (resolver datos antes con estrategia autorizada).

-- ========== Cambio propuesto (ejecutar solo tras autorización y conteos en cero) ==========
CREATE OR REPLACE FUNCTION inventario.trg_producto_clasificacion_exclusiva()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'perecedero' THEN
        IF EXISTS (SELECT 1 FROM inventario.no_perecedero np WHERE np.id_producto = NEW.id_producto) THEN
            RAISE EXCEPTION 'El producto ya está clasificado como no perecedero.';
        END IF;
    ELSIF TG_TABLE_NAME = 'no_perecedero' THEN
        IF EXISTS (SELECT 1 FROM inventario.perecedero pe WHERE pe.id_producto = NEW.id_producto) THEN
            RAISE EXCEPTION 'El producto ya está clasificado como perecedero.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_perecedero_exclusivo
    BEFORE INSERT OR UPDATE OF id_producto ON inventario.perecedero
    FOR EACH ROW EXECUTE FUNCTION inventario.trg_producto_clasificacion_exclusiva();

CREATE TRIGGER tg_no_perecedero_exclusivo
    BEFORE INSERT OR UPDATE OF id_producto ON inventario.no_perecedero
    FOR EACH ROW EXECUTE FUNCTION inventario.trg_producto_clasificacion_exclusiva();

-- ========== Verificación posterior ==========
-- INSERT de prueba en entorno aislado (no en producción sin plan de limpieza).

-- ========== Reversión ==========
-- DROP TRIGGER IF EXISTS tg_perecedero_exclusivo ON inventario.perecedero;
-- DROP TRIGGER IF EXISTS tg_no_perecedero_exclusivo ON inventario.no_perecedero;
-- DROP FUNCTION IF EXISTS inventario.trg_producto_clasificacion_exclusiva();
