-- MIG-12.5A — Procedimientos de salida normal (perecedero FEFO multilote y no perecedero)
-- Esquema: inventario
-- NO modifica tablas existentes. Solo crea/reemplaza funciones y procedimientos nuevos.

SET search_path = inventario, public;

-- ---------------------------------------------------------------------------
-- fn_validar_salida — validaciones comunes (una bodega, sin destino)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inventario.fn_validar_salida(
    p_id_bodega integer,
    p_id_producto integer,
    p_cantidad integer
) RETURNS boolean
LANGUAGE plpgsql
AS $function$
DECLARE
    v_stock integer;
BEGIN
    IF p_cantidad IS NULL OR p_cantidad <= 0 THEN
        RAISE EXCEPTION 'La cantidad debe ser mayor que cero';
    END IF;

    IF p_id_bodega IS NULL THEN
        RAISE EXCEPTION 'La bodega no existe';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM bodega WHERE id_bodega = p_id_bodega
    ) THEN
        RAISE EXCEPTION 'La bodega no existe';
    END IF;

    IF p_id_producto IS NULL THEN
        RAISE EXCEPTION 'El producto no existe';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM producto WHERE id_producto = p_id_producto
    ) THEN
        RAISE EXCEPTION 'El producto no existe';
    END IF;

    v_stock := fn_consultar_stock_bodega(p_id_bodega, p_id_producto);

    IF v_stock < p_cantidad THEN
        RAISE EXCEPTION
            'Stock insuficiente. Disponible: %, solicitado: %',
            v_stock, p_cantidad;
    END IF;

    RETURN TRUE;
END;
$function$;

-- ---------------------------------------------------------------------------
-- fn_stock_lotes_vigentes — suma disponible en lotes no vencidos
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inventario.fn_stock_lotes_vigentes(
    p_id_bodega integer,
    p_id_producto integer
) RETURNS integer
LANGUAGE sql
STABLE
AS $function$
    SELECT COALESCE(SUM(l.cantidad_actual), 0)::integer
    FROM lote l
    WHERE l.id_bodega = p_id_bodega
      AND l.id_producto = p_id_producto
      AND l.cantidad_actual > 0
      AND l.fech_vencimiento > CURRENT_DATE;
$function$;

-- ---------------------------------------------------------------------------
-- sp_salida_no_perecedero
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE inventario.sp_salida_no_perecedero(
    IN p_id_bodega integer,
    IN p_id_producto integer,
    IN p_cantidad integer,
    IN p_id_usuario integer,
    OUT p_id_movimiento integer
)
LANGUAGE plpgsql
AS $procedure$
BEGIN
    PERFORM fn_validar_salida(p_id_bodega, p_id_producto, p_cantidad);

  PERFORM 1
    FROM bodega
    WHERE id_bodega = p_id_bodega
    FOR UPDATE;

    PERFORM fn_validar_salida(p_id_bodega, p_id_producto, p_cantidad);

    IF NOT EXISTS (
        SELECT 1 FROM no_perecedero WHERE id_producto = p_id_producto
    ) THEN
        RAISE EXCEPTION 'El producto no es no perecedero';
    END IF;

    IF EXISTS (
        SELECT 1 FROM perecedero WHERE id_producto = p_id_producto
    ) THEN
        RAISE EXCEPTION 'El producto no es no perecedero';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM usuario
        WHERE id_usuario = p_id_usuario
          AND activo = TRUE
    ) THEN
        RAISE EXCEPTION 'El usuario no existe o esta inactivo';
    END IF;

    PERFORM 1
    FROM inventario
    WHERE id_bodega = p_id_bodega
      AND id_producto = p_id_producto
    FOR UPDATE;

    UPDATE inventario
    SET cantidad_actual = cantidad_actual - p_cantidad
    WHERE id_bodega = p_id_bodega
      AND id_producto = p_id_producto
      AND cantidad_actual >= p_cantidad;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Stock insuficiente o inventario no encontrado en la bodega';
    END IF;

    INSERT INTO movimiento (tipo_movimiento, cantidad, id_bodega, id_usuario, id_producto)
    VALUES ('SALIDA', p_cantidad, p_id_bodega, p_id_usuario, p_id_producto)
    RETURNING id_movimiento INTO p_id_movimiento;
END;
$procedure$;

-- ---------------------------------------------------------------------------
-- sp_salida_perecedero — FEFO automático (varios movimientos si aplica)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE inventario.sp_salida_perecedero(
    IN p_id_bodega integer,
    IN p_id_producto integer,
    IN p_cantidad integer,
    IN p_id_usuario integer,
    OUT p_id_movimiento integer
)
LANGUAGE plpgsql
AS $procedure$
DECLARE
    v_restante integer;
    v_tomar integer;
    v_stock_lotes integer;
    r_lote RECORD;
    v_id_mov integer;
BEGIN
    PERFORM fn_validar_salida(p_id_bodega, p_id_producto, p_cantidad);

    PERFORM 1
    FROM bodega
    WHERE id_bodega = p_id_bodega
    FOR UPDATE;

    PERFORM fn_validar_salida(p_id_bodega, p_id_producto, p_cantidad);

    IF NOT EXISTS (
        SELECT 1 FROM perecedero WHERE id_producto = p_id_producto
    ) THEN
        RAISE EXCEPTION 'El producto no es perecedero';
    END IF;

    IF EXISTS (
        SELECT 1 FROM no_perecedero WHERE id_producto = p_id_producto
    ) THEN
        RAISE EXCEPTION 'El producto no es perecedero';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM usuario
        WHERE id_usuario = p_id_usuario
          AND activo = TRUE
    ) THEN
        RAISE EXCEPTION 'El usuario no existe o esta inactivo';
    END IF;

    v_stock_lotes := fn_stock_lotes_vigentes(p_id_bodega, p_id_producto);

    IF v_stock_lotes < p_cantidad THEN
        RAISE EXCEPTION
            'Sin lotes vigentes suficientes. Disponible en lotes: %, solicitado: %',
            v_stock_lotes, p_cantidad;
    END IF;

    PERFORM 1
    FROM inventario
    WHERE id_bodega = p_id_bodega
      AND id_producto = p_id_producto
    FOR UPDATE;

    IF NOT EXISTS (
        SELECT 1
        FROM inventario
        WHERE id_bodega = p_id_bodega
          AND id_producto = p_id_producto
          AND cantidad_actual >= p_cantidad
    ) THEN
        RAISE EXCEPTION 'Inventario inconsistente o stock insuficiente';
    END IF;

    v_restante := p_cantidad;
    p_id_movimiento := NULL;

    FOR r_lote IN
        SELECT l.num_lote, l.cantidad_actual
        FROM lote l
        WHERE l.id_bodega = p_id_bodega
          AND l.id_producto = p_id_producto
          AND l.cantidad_actual > 0
          AND l.fech_vencimiento > CURRENT_DATE
        ORDER BY l.fech_vencimiento ASC, l.num_lote ASC
        FOR UPDATE
    LOOP
        EXIT WHEN v_restante <= 0;

        v_tomar := LEAST(v_restante, r_lote.cantidad_actual);

        UPDATE lote
        SET cantidad_actual = cantidad_actual - v_tomar
        WHERE num_lote = r_lote.num_lote
          AND id_bodega = p_id_bodega
          AND id_producto = p_id_producto
          AND cantidad_actual >= v_tomar;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Cantidad insuficiente en el lote %', r_lote.num_lote;
        END IF;

        INSERT INTO movimiento (
            tipo_movimiento, cantidad, id_bodega, id_usuario, id_producto, num_lote
        ) VALUES (
            'SALIDA', v_tomar, p_id_bodega, p_id_usuario, p_id_producto, r_lote.num_lote
        ) RETURNING id_movimiento INTO v_id_mov;

        IF p_id_movimiento IS NULL THEN
            p_id_movimiento := v_id_mov;
        END IF;

        v_restante := v_restante - v_tomar;
    END LOOP;

    IF v_restante > 0 THEN
        RAISE EXCEPTION
            'No fue posible completar la salida. Faltan % unidades en lotes vigentes',
            v_restante;
    END IF;

    UPDATE inventario
    SET cantidad_actual = cantidad_actual - p_cantidad
    WHERE id_bodega = p_id_bodega
      AND id_producto = p_id_producto
      AND cantidad_actual >= p_cantidad;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Inventario insuficiente al registrar la salida';
    END IF;
END;
$procedure$;
