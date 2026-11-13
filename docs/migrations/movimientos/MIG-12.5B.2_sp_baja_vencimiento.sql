-- MIG-12.5B.2 — Baja por vencimiento (solo perecedero, lote explícito vencido)
-- Reutiliza fn_usuario_es_administrador y fn_validar_motivo_baja (12.5B.1).
-- No modifica tablas ni procedimientos de salida / baja por daño.

SET search_path = inventario, public;

CREATE OR REPLACE PROCEDURE inventario.sp_baja_vencimiento(
    IN p_id_bodega integer,
    IN p_id_producto integer,
    IN p_num_lote integer,
    IN p_cantidad integer,
    IN p_id_usuario integer,
    IN p_motivo text,
    OUT p_id_movimiento integer
)
LANGUAGE plpgsql
AS $procedure$
DECLARE
    v_motivo text;
    v_desc text;
    v_lote_qty integer;
    v_fecha_venc date;
BEGIN
    IF p_num_lote IS NULL THEN
        RAISE EXCEPTION 'Debe indicar un lote';
    END IF;

    v_motivo := fn_validar_motivo_baja(p_motivo);
    PERFORM fn_usuario_es_administrador(p_id_usuario);
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

    PERFORM 1
    FROM inventario
    WHERE id_bodega = p_id_bodega
      AND id_producto = p_id_producto
    FOR UPDATE;

    SELECT l.cantidad_actual, l.fech_vencimiento
    INTO v_lote_qty, v_fecha_venc
    FROM lote l
    WHERE l.num_lote = p_num_lote
      AND l.id_bodega = p_id_bodega
      AND l.id_producto = p_id_producto
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El lote no existe en esta bodega para el producto';
    END IF;

    IF v_fecha_venc > CURRENT_DATE THEN
        RAISE EXCEPTION 'El lote aun no esta vencido';
    END IF;

    IF v_lote_qty < p_cantidad THEN
        RAISE EXCEPTION
            'Cantidad insuficiente en el lote. Disponible: %, solicitado: %',
            v_lote_qty, p_cantidad;
    END IF;

    UPDATE lote
    SET cantidad_actual = cantidad_actual - p_cantidad
    WHERE num_lote = p_num_lote
      AND id_bodega = p_id_bodega
      AND id_producto = p_id_producto
      AND cantidad_actual >= p_cantidad;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Cantidad insuficiente en el lote';
    END IF;

    UPDATE inventario
    SET cantidad_actual = cantidad_actual - p_cantidad
    WHERE id_bodega = p_id_bodega
      AND id_producto = p_id_producto
      AND cantidad_actual >= p_cantidad;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Inventario insuficiente al registrar la baja';
    END IF;

    INSERT INTO movimiento (
        tipo_movimiento, cantidad, id_bodega, id_usuario, id_producto, num_lote
    ) VALUES (
        'BAJA_VENCIMIENTO',
        p_cantidad,
        p_id_bodega,
        p_id_usuario,
        p_id_producto,
        p_num_lote
    ) RETURNING id_movimiento INTO p_id_movimiento;

    v_desc := format(
        'Baja por vencimiento movimiento #%s lote %s: %s',
        p_id_movimiento,
        p_num_lote,
        v_motivo
    );
    v_desc := left(v_desc, 500);

    INSERT INTO bitacora (id_usuario, tipo_accion, tabla_afectada, descripcion)
    VALUES (p_id_usuario, 'BAJA_VENCIMIENTO', 'movimiento', v_desc);
END;
$procedure$;
