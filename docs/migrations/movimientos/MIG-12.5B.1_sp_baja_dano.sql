-- MIG-12.5B.1 — Bajas por daño (perecedero y no perecedero)
-- Esquema: inventario. No altera tablas ni procedimientos de salida existentes.

SET search_path = inventario, public;

-- ---------------------------------------------------------------------------
-- fn_usuario_es_administrador — valida id_usuario contra tabla rol (no texto libre)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inventario.fn_usuario_es_administrador(p_id_usuario integer)
RETURNS void
LANGUAGE plpgsql
AS $function$
BEGIN
    IF p_id_usuario IS NULL THEN
        RAISE EXCEPTION 'El usuario no existe o esta inactivo';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM usuario u
        INNER JOIN rol r ON r.id_rol = u.id_rol
        WHERE u.id_usuario = p_id_usuario
          AND u.activo = TRUE
          AND NOT u.bloqueado
          AND r.activo = TRUE
          AND r.nombre = 'ADMINISTRADOR'
    ) THEN
        RAISE EXCEPTION
            'Solo un administrador puede ejecutar bajas por daño';
    END IF;
END;
$function$;

-- ---------------------------------------------------------------------------
-- fn_validar_motivo_baja — motivo obligatorio
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inventario.fn_validar_motivo_baja(p_motivo text)
RETURNS text
LANGUAGE plpgsql
IMMUTABLE
AS $function$
DECLARE
    v_trim text;
BEGIN
    IF p_motivo IS NULL THEN
        RAISE EXCEPTION 'El motivo de la baja es obligatorio';
    END IF;

    v_trim := btrim(p_motivo);

    IF v_trim = '' THEN
        RAISE EXCEPTION 'El motivo de la baja es obligatorio';
    END IF;

    IF char_length(v_trim) > 500 THEN
        RAISE EXCEPTION
            'El motivo excede el maximo permitido (500 caracteres)';
    END IF;

    RETURN v_trim;
END;
$function$;

-- ---------------------------------------------------------------------------
-- sp_baja_dano_no_perecedero
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE inventario.sp_baja_dano_no_perecedero(
    IN p_id_bodega integer,
    IN p_id_producto integer,
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
BEGIN
    v_motivo := fn_validar_motivo_baja(p_motivo);
    PERFORM fn_usuario_es_administrador(p_id_usuario);
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
    VALUES ('BAJA_DAÑO', p_cantidad, p_id_bodega, p_id_usuario, p_id_producto)
    RETURNING id_movimiento INTO p_id_movimiento;

    v_desc := format(
        'Baja por dano movimiento #%s: %s',
        p_id_movimiento,
        v_motivo
    );
    v_desc := left(v_desc, 500);

    INSERT INTO bitacora (id_usuario, tipo_accion, tabla_afectada, descripcion)
    VALUES (p_id_usuario, 'BAJA_DAÑO', 'movimiento', v_desc);
END;
$procedure$;

-- ---------------------------------------------------------------------------
-- sp_baja_dano_perecedero — lote explícito (vigente o vencido)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE inventario.sp_baja_dano_perecedero(
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

    SELECT l.cantidad_actual
    INTO v_lote_qty
    FROM lote l
    WHERE l.num_lote = p_num_lote
      AND l.id_bodega = p_id_bodega
      AND l.id_producto = p_id_producto
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El lote no existe en esta bodega para el producto';
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
        'BAJA_DAÑO', p_cantidad, p_id_bodega, p_id_usuario, p_id_producto, p_num_lote
    ) RETURNING id_movimiento INTO p_id_movimiento;

    v_desc := format(
        'Baja por dano movimiento #%s lote %s: %s',
        p_id_movimiento,
        p_num_lote,
        v_motivo
    );
    v_desc := left(v_desc, 500);

    INSERT INTO bitacora (id_usuario, tipo_accion, tabla_afectada, descripcion)
    VALUES (p_id_usuario, 'BAJA_DAÑO', 'movimiento', v_desc);
END;
$procedure$;
