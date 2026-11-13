"""Validación integral 12.5B.3 — bajas (sin modificar SP ni esquema)."""
from __future__ import annotations

import sys
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

try:
    import psycopg2
except ImportError:
    sys.stderr.write("pip install psycopg2-binary\n")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[3]
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_test_12_5b_3_output.txt"

LOT_VIG = 99701
LOT_VEN = 99702
LOT_HOY = 99703


def load_props(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def jdbc_conninfo(jdbc_url: str, user: str, password: str) -> str:
    if jdbc_url.startswith("jdbc:"):
        jdbc_url = jdbc_url[5:]
    rest = jdbc_url.replace("postgresql://", "", 1)
    host_db = rest.split("?")[0]
    query = rest.split("?")[1] if "?" in rest else "sslmode=require"
    hostport, dbname = host_db.rsplit("/", 1)
    host = hostport.split(":")[0]
    port = hostport.split(":")[1] if ":" in hostport else "5432"
    opts = query.replace("&", " ")
    return f"host={host} port={port} dbname={dbname} user={user} password={password} {opts}"


def connect(props: dict[str, str]):
    return psycopg2.connect(
        jdbc_conninfo(
            props["spring.datasource.url"],
            props["spring.datasource.username"],
            props["spring.datasource.password"],
        )
    )


def call_np(cur, bodega, producto, qty, usuario, motivo) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_dano_no_perecedero(%s,%s,%s,%s,%s,NULL)",
        (bodega, producto, qty, usuario, motivo),
    )
    row = cur.fetchone()
    return row[0] if row else None


def call_dano_per(cur, bodega, producto, lote, qty, usuario, motivo) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_dano_perecedero(%s,%s,%s,%s,%s,%s,NULL)",
        (bodega, producto, lote, qty, usuario, motivo),
    )
    row = cur.fetchone()
    return row[0] if row else None


def call_venc(cur, bodega, producto, lote, qty, usuario, motivo) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_vencimiento(%s,%s,%s,%s,%s,%s,NULL)",
        (bodega, producto, lote, qty, usuario, motivo),
    )
    row = cur.fetchone()
    return row[0] if row else None


def expect_pg_error(fn) -> bool:
    try:
        fn()
        return False
    except psycopg2.Error:
        return True


def fetch_user(cur, rol: str) -> int | None:
    cur.execute(
        """
        SELECT u.id_usuario FROM inventario.usuario u
        JOIN inventario.rol r ON r.id_rol = u.id_rol
        WHERE r.nombre=%s AND u.activo=TRUE AND NOT u.bloqueado AND r.activo=TRUE
        LIMIT 1
        """,
        (rol,),
    )
    row = cur.fetchone()
    return row[0] if row else None


def verify_baja_ok(
    cur,
    mov_id: int,
    tipo: str,
    qty: int,
    usuario: int,
    inv_before: int,
    inv_after: int,
    bodega: int,
    producto: int,
    lote: int | None,
    lot_before: int | None,
    lot_after: int | None,
    motivo_fragment: str,
) -> None:
    if inv_after != inv_before - qty:
        raise AssertionError(f"inventario {inv_before}->{inv_after}")
    if lote is not None and lot_after != lot_before - qty:
        raise AssertionError(f"lote {lot_before}->{lot_after}")
    cur.execute(
        """
        SELECT tipo_movimiento, cantidad, id_usuario, id_bodega, id_producto, num_lote
        FROM inventario.movimiento WHERE id_movimiento=%s
        """,
        (mov_id,),
    )
    m = cur.fetchone()
    if m[0] != tipo or m[1] != qty or m[2] != usuario:
        raise AssertionError(f"movimiento {m}")
    if m[3] != bodega or m[4] != producto:
        raise AssertionError(f"movimiento FK {m}")
    if lote is not None and m[5] != lote:
        raise AssertionError(f"num_lote movimiento {m}")
    cur.execute(
        """
        SELECT COUNT(*) FROM inventario.movimiento WHERE id_movimiento=%s
        """,
        (mov_id,),
    )
    if cur.fetchone()[0] != 1:
        raise AssertionError("movimiento duplicado")
    cur.execute(
        """
        SELECT id_usuario, tipo_accion, tabla_afectada, descripcion, fecha_hora
        FROM inventario.bitacora
        WHERE descripcion LIKE %s
        ORDER BY id_bitacora DESC LIMIT 1
        """,
        (f"%movimiento #{mov_id}%",),
    )
    b = cur.fetchone()
    if not b or b[0] != usuario or motivo_fragment not in b[3]:
        raise AssertionError(f"bitacora {b}")
    if b[2] != "movimiento":
        raise AssertionError("tabla_afectada bitacora")
    if (datetime.now(timezone.utc) - b[4]).total_seconds() > 120:
        raise AssertionError("fecha_hora bitacora fuera de tolerancia")
    cur.execute(
        "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
        (bodega, producto),
    )
    if cur.fetchone()[0] < 0:
        raise AssertionError("inventario negativo")
    if lote is not None:
        cur.execute(
            """
            SELECT cantidad_actual FROM inventario.lote
            WHERE id_bodega=%s AND id_producto=%s AND num_lote=%s
            """,
            (bodega, producto, lote),
        )
        if cur.fetchone()[0] < 0:
            raise AssertionError("lote negativo")


@dataclass
class WorkerOutcome:
    label: str
    ok: bool
    detail: str


def run_two(
    props,
    label: str,
    worker,
) -> tuple[WorkerOutcome, WorkerOutcome]:
    barrier = threading.Barrier(2)
    outcomes: list[WorkerOutcome] = []
    lock = threading.Lock()

    def wrap(lbl: str):
        conn = connect(props)
        conn.autocommit = False
        try:
            barrier.wait(timeout=60)
            worker(conn, lbl)
            conn.commit()
            with lock:
                outcomes.append(WorkerOutcome(lbl, True, "commit"))
        except Exception as exc:
            conn.rollback()
            with lock:
                outcomes.append(
                    WorkerOutcome(lbl, False, f"{type(exc).__name__}: {exc}")
                )
        finally:
            conn.close()

    t1 = threading.Thread(target=wrap, args=("S1",))
    t2 = threading.Thread(target=wrap, args=("S2",))
    t1.start()
    t2.start()
    t1.join(120)
    t2.join(120)
    by = {o.label: o for o in outcomes}
    return by["S1"], by["S2"]


def main() -> int:
    props = load_props(PROPS)
    lines: list[str] = []
    failed = 0
    incidencias: list[str] = []

    def log(msg: str) -> None:
        lines.append(msg)

    def fail(name: str, detail: str) -> None:
        nonlocal failed
        failed += 1
        log(f"FAIL {name}: {detail}")
        incidencias.append(f"{name}: {detail}")

    def ok(name: str) -> None:
        log(f"OK {name}")

    conn = connect(props)
    conn.autocommit = False

    try:
        with conn.cursor() as cur:
            id_admin = fetch_user(cur, "ADMINISTRADOR")
            id_oper = fetch_user(cur, "OPERATIVO")
            log(f"=== ENTORNO admin={id_admin} operativo={id_oper} ===")

            cur.execute(
                """
                SELECT p.id_producto, i.id_bodega, i.cantidad_actual
                FROM inventario.no_perecedero np
                JOIN inventario.producto p ON p.id_producto=np.id_producto
                JOIN inventario.inventario i ON i.id_producto=p.id_producto
                WHERE i.cantidad_actual >= 10
                LIMIT 1
                """
            )
            np_fix = cur.fetchone()
            cur.execute(
                """
                SELECT p.id_producto FROM inventario.perecedero pe
                JOIN inventario.producto p ON p.id_producto=pe.id_producto LIMIT 1
                """
            )
            per_fix = cur.fetchone()
            cur.execute(
                "SELECT id_bodega FROM inventario.bodega ORDER BY id_bodega LIMIT 1"
            )
            id_bodega = cur.fetchone()[0]
            cur.execute(
                "SELECT id_proveedor FROM inventario.proveedor ORDER BY id_proveedor LIMIT 1"
            )
            id_prov = cur.fetchone()[0]
            cur.execute(
                """
                SELECT p.id_producto FROM inventario.perecedero pe
                JOIN inventario.producto p ON p.id_producto=pe.id_producto
                WHERE p.id_producto <> %s LIMIT 1
                """,
                (per_fix[0] if per_fix else -1,),
            )
            per_alt = cur.fetchone()

            mot = "Integral 12.5B.3"

            # --- A: no perecedero ---
            log("=== A. Baja daño no perecedero ===")
            if np_fix:
                b, p = np_fix[1], np_fix[0]

                def run_a(name, fn, expect_ok=True):
                    cur.execute("SAVEPOINT sp_a")
                    try:
                        if expect_ok:
                            fn()
                            ok(name)
                        elif expect_pg_error(fn):
                            ok(f"{name} (rechazado)")
                        else:
                            fail(name, "esperaba error PG")
                    except Exception as exc:
                        fail(name, str(exc))
                    finally:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_a")

                def a_valida():
                    inv0 = np_fix[2]
                    cur.execute(
                        "UPDATE inventario.inventario SET cantidad_actual=50 WHERE id_bodega=%s AND id_producto=%s",
                        (b, p),
                    )
                    inv0 = 50
                    mov = call_np(cur, b, p, 3, id_admin, mot)
                    inv1 = cur.execute(
                        "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                        (b, p),
                    ) or None
                    cur.execute(
                        "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                        (b, p),
                    )
                    inv1 = cur.fetchone()[0]
                    verify_baja_ok(
                        cur, mov, "BAJA_DAÑO", 3, id_admin, inv0, inv1, b, p, None, None, None, mot
                    )

                run_a("A01_valida", a_valida)
                run_a("A02_insuficiente", lambda: call_np(cur, b, p, 9_999, id_admin, mot), False)
                run_a("A03_cero", lambda: call_np(cur, b, p, 0, id_admin, mot), False)
                run_a("A04_negativa", lambda: call_np(cur, b, p, -1, id_admin, mot), False)
                run_a("A05_motivo_vacio", lambda: call_np(cur, b, p, 1, id_admin, "  "), False)
                run_a(
                    "A06_motivo_501",
                    lambda: call_np(cur, b, p, 1, id_admin, "x" * 501),
                    False,
                )

            # --- B: daño perecedero ---
            log("=== B. Baja daño perecedero ===")
            if per_fix:
                prod = per_fix[0]

                def seed_per():
                    cur.execute(
                        """
                        INSERT INTO inventario.inventario (id_bodega,id_producto,cantidad_actual,stock_minimo)
                        VALUES (%s,%s,80,0) ON CONFLICT (id_bodega,id_producto)
                        DO UPDATE SET cantidad_actual=80
                        """,
                        (id_bodega, prod),
                    )
                    for lot, fe, q in (
                        (LOT_VIG, "CURRENT_DATE + 60", 25),
                        (LOT_VEN, "CURRENT_DATE - 5", 20),
                    ):
                        cur.execute(
                            f"""
                            INSERT INTO inventario.lote (
                              num_lote,id_bodega,id_producto,id_proveedor,
                              fech_vencimiento,cantidad_recibida,cantidad_actual
                            ) VALUES (%s,%s,%s,%s,{fe},%s,%s)
                            ON CONFLICT (num_lote,id_bodega,id_producto)
                            DO UPDATE SET cantidad_actual=EXCLUDED.cantidad_actual,
                              fech_vencimiento=EXCLUDED.fech_vencimiento
                            """,
                            (lot, id_bodega, prod, id_prov, q, q),
                        )

                def run_b(name, fn, expect_ok=True):
                    cur.execute("SAVEPOINT sp_b")
                    seed_per()
                    try:
                        if expect_ok:
                            fn()
                            ok(name)
                        elif expect_pg_error(fn):
                            ok(f"{name} (rechazado)")
                        else:
                            fail(name, "esperaba error")
                    except Exception as exc:
                        fail(name, str(exc))
                    finally:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_b")

                def b_vigente():
                    li0 = 25
                    inv0 = 80
                    mov = call_dano_per(cur, id_bodega, prod, LOT_VIG, 4, id_admin, mot)
                    cur.execute(
                        "SELECT cantidad_actual FROM inventario.lote WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s",
                        (LOT_VIG, id_bodega, prod),
                    )
                    li1 = cur.fetchone()[0]
                    cur.execute(
                        "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                        (id_bodega, prod),
                    )
                    inv1 = cur.fetchone()[0]
                    verify_baja_ok(
                        cur, mov, "BAJA_DAÑO", 4, id_admin, inv0, inv1, id_bodega, prod,
                        LOT_VIG, li0, li1, mot
                    )

                def b_vencido():
                    mov = call_dano_per(cur, id_bodega, prod, LOT_VEN, 2, id_admin, mot)

                def b_parcial():
                    call_dano_per(cur, id_bodega, prod, LOT_VIG, 5, id_admin, mot)

                def b_total():
                    call_dano_per(cur, id_bodega, prod, LOT_VEN, 20, id_admin, mot)
                    cur.execute(
                        "SELECT cantidad_actual FROM inventario.lote WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s",
                        (LOT_VEN, id_bodega, prod),
                    )
                    if cur.fetchone()[0] != 0:
                        raise AssertionError("lote no quedó en cero")

                run_b("B01_lote_vigente", b_vigente)
                run_b("B02_lote_vencido", b_vencido)
                run_b("B03_parcial", b_parcial)
                run_b("B04_total_lote", b_total)
                run_b(
                    "B05_lote_inexistente",
                    lambda: call_dano_per(cur, id_bodega, prod, 99991, 1, id_admin, mot),
                    False,
                )
                if per_alt:
                    run_b(
                        "B06_lote_otro_producto",
                        lambda: call_dano_per(
                            cur, id_bodega, per_alt[0], LOT_VIG, 1, id_admin, mot
                        ),
                        False,
                    )
                run_b(
                    "B07_insuficiente",
                    lambda: call_dano_per(cur, id_bodega, prod, LOT_VIG, 999, id_admin, mot),
                    False,
                )

            # --- C: vencimiento ---
            log("=== C. Baja vencimiento ===")
            if per_fix:
                prod = per_fix[0]

                def seed_venc():
                    cur.execute(
                        """
                        INSERT INTO inventario.inventario (id_bodega,id_producto,cantidad_actual,stock_minimo)
                        VALUES (%s,%s,70,0) ON CONFLICT DO NOTHING
                        """,
                        (id_bodega, prod),
                    )
                    cur.execute(
                        """
                        UPDATE inventario.inventario SET cantidad_actual=70
                        WHERE id_bodega=%s AND id_producto=%s
                        """,
                        (id_bodega, prod),
                    )
                    for lot, fe, q in (
                        (LOT_VIG, "CURRENT_DATE + 30", 20),
                        (LOT_VEN, "CURRENT_DATE - 2", 18),
                        (LOT_HOY, "CURRENT_DATE", 12),
                    ):
                        cur.execute(
                            f"""
                            INSERT INTO inventario.lote (
                              num_lote,id_bodega,id_producto,id_proveedor,
                              fech_vencimiento,cantidad_recibida,cantidad_actual
                            ) VALUES (%s,%s,%s,%s,{fe},%s,%s)
                            ON CONFLICT (num_lote,id_bodega,id_producto)
                            DO UPDATE SET cantidad_actual=EXCLUDED.cantidad_actual,
                              fech_vencimiento=EXCLUDED.fech_vencimiento
                            """,
                            (lot, id_bodega, prod, id_prov, q, q),
                        )

                def run_c(name, fn, expect_ok=True):
                    cur.execute("SAVEPOINT sp_c")
                    seed_venc()
                    try:
                        if expect_ok:
                            fn()
                            ok(name)
                        elif expect_pg_error(fn):
                            ok(f"{name} (rechazado)")
                        else:
                            fail(name, "esperaba error")
                    except Exception as exc:
                        fail(name, str(exc))
                    finally:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_c")

                run_c("C01_vencido", lambda: call_venc(cur, id_bodega, prod, LOT_VEN, 3, id_admin, mot))
                run_c("C02_vencido_hoy", lambda: call_venc(cur, id_bodega, prod, LOT_HOY, 1, id_admin, mot))
                run_c(
                    "C03_vigente_rechazo",
                    lambda: call_venc(cur, id_bodega, prod, LOT_VIG, 1, id_admin, mot),
                    False,
                )
                run_c("C04_parcial", lambda: call_venc(cur, id_bodega, prod, LOT_VEN, 5, id_admin, mot))
                run_c("C05_total", lambda: call_venc(cur, id_bodega, prod, LOT_HOY, 12, id_admin, mot))
                run_c(
                    "C06_insuficiente",
                    lambda: call_venc(cur, id_bodega, prod, LOT_VEN, 500, id_admin, mot),
                    False,
                )

            # --- Seguridad ---
            log("=== SEGURIDAD ===")
            if np_fix and id_admin and id_oper:
                b, p = np_fix[1], np_fix[0]
                cur.execute("SAVEPOINT sp_sec")

                def sec(name, uid, expect_ok):
                    cur.execute("SAVEPOINT sp_sec_case")
                    try:
                        if expect_ok:
                            call_np(cur, b, p, 1, uid, mot)
                            ok(name)
                        elif expect_pg_error(lambda: call_np(cur, b, p, 1, uid, mot)):
                            ok(f"{name} (rechazado)")
                        else:
                            fail(name, "debía rechazar")
                    finally:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_sec_case")

                sec("SEC_admin", id_admin, True)
                sec("SEC_operativo", id_oper, False)
                sec("SEC_inexistente", -99999, False)

                cur.execute(
                    "SELECT id_usuario FROM inventario.usuario WHERE activo=FALSE LIMIT 1"
                )
                inact = cur.fetchone()
                if inact:
                    sec("SEC_inactivo_existente", inact[0], False)
                else:
                    cur.execute("SAVEPOINT sp_inact")
                    cur.execute(
                        "UPDATE inventario.usuario SET activo=FALSE WHERE id_usuario=%s",
                        (id_oper,),
                    )
                    if expect_pg_error(lambda: call_np(cur, b, p, 1, id_oper, mot)):
                        ok("SEC_inactivo_simulado (rechazado)")
                    else:
                        fail("SEC_inactivo_simulado", "no rechazó")
                    cur.execute("ROLLBACK TO SAVEPOINT sp_inact")

                cur.execute(
                    "SELECT id_usuario FROM inventario.usuario WHERE bloqueado=TRUE LIMIT 1"
                )
                bloc = cur.fetchone()
                if bloc:
                    sec("SEC_bloqueado_existente", bloc[0], False)
                else:
                    cur.execute("SAVEPOINT sp_bloc")
                    cur.execute(
                        "UPDATE inventario.usuario SET bloqueado=TRUE WHERE id_usuario=%s",
                        (id_oper,),
                    )
                    if expect_pg_error(lambda: call_np(cur, b, p, 1, id_oper, mot)):
                        ok("SEC_bloqueado_simulado (rechazado)")
                    else:
                        fail("SEC_bloqueado_simulado", "no rechazó")
                    cur.execute("ROLLBACK TO SAVEPOINT sp_bloc")

                cur.execute("ROLLBACK TO SAVEPOINT sp_sec")

            # --- Rollback inventario (motivo vacío tras seed) ---
            log("=== ROLLBACK ===")
            if per_fix:
                prod = per_fix[0]
                cur.execute("SAVEPOINT sp_roll")
                cur.execute(
                    """
                    UPDATE inventario.inventario SET cantidad_actual=40
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (id_bodega, prod),
                )
                cur.execute(
                    """
                    INSERT INTO inventario.lote (
                      num_lote,id_bodega,id_producto,id_proveedor,
                      fech_vencimiento,cantidad_recibida,cantidad_actual
                    ) VALUES (%s,%s,%s,%s,CURRENT_DATE+10,10,10)
                    ON CONFLICT (num_lote,id_bodega,id_producto)
                    DO UPDATE SET cantidad_actual=10
                    """,
                    (LOT_VIG, id_bodega, prod, id_prov),
                )
                inv_b = 40
                lot_b = 10
                cur.execute("SAVEPOINT sp_roll_err")
                try:
                    call_dano_per(cur, id_bodega, prod, LOT_VIG, 2, id_admin, " ")
                    fail("ROLL_motivo_vacio", "debía fallar")
                except psycopg2.Error:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_roll_err")
                cur.execute(
                    "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                    (id_bodega, prod),
                )
                inv_a = cur.fetchone()[0]
                cur.execute(
                    "SELECT cantidad_actual FROM inventario.lote WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s",
                    (LOT_VIG, id_bodega, prod),
                )
                lot_a = cur.fetchone()[0]
                if inv_a == inv_b and lot_a == lot_b:
                    ok("ROLL_sin_parciales_motivo_vacio")
                else:
                    fail("ROLL_sin_parciales", f"inv {inv_b}->{inv_a} lot {lot_b}->{lot_a}")
                cur.execute("ROLLBACK TO SAVEPOINT sp_roll")

        conn.rollback()
        log("ROLLBACK conexión funcional (sin datos persistentes de suite savepoint)")

    finally:
        conn.close()

    # --- Concurrencia con restauración ---
    if not (np_fix and per_fix and id_admin):
        log("SKIP concurrencia: fixtures incompletos")
    else:
        admin_conn = connect(props)
        admin_conn.autocommit = False
        id_prod_np, id_bod_np = np_fix[0], np_fix[1]
        id_prod_per = per_fix[0]
        mov_max0 = 0
        bit_max0 = 0
        np_inv_orig = 0
        try:
            with admin_conn.cursor() as cur:
                cur.execute("SELECT COALESCE(MAX(id_movimiento),0) FROM inventario.movimiento")
                mov_max0 = cur.fetchone()[0]
                cur.execute("SELECT COALESCE(MAX(id_bitacora),0) FROM inventario.bitacora")
                bit_max0 = cur.fetchone()[0]
                cur.execute(
                    "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                    (id_bod_np, id_prod_np),
                )
                np_inv_orig = cur.fetchone()[0]

            log("=== CONC A: dos bajas NP mismo inventario ===")
            with admin_conn.cursor() as cur:
                cur.execute(
                    """
                    UPDATE inventario.inventario SET cantidad_actual=25
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (id_bod_np, id_prod_np),
                )
            admin_conn.commit()

            def w_np(conn, lbl):
                with conn.cursor() as c:
                    call_np(c, id_bod_np, id_prod_np, 15, id_admin, f"Conc {lbl}")

            s1, s2 = run_two(props, "A", lambda c, l: w_np(c, l))
            log(f"CONC_A S1 ok={s1.ok} {s1.detail}")
            log(f"CONC_A S2 ok={s2.ok} {s2.detail}")
            wins = sum(1 for s in (s1, s2) if s.ok)
            with admin_conn.cursor() as cur:
                cur.execute(
                    "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                    (id_bod_np, id_prod_np),
                )
                st = cur.fetchone()[0]
                cur.execute(
                    """
                    SELECT COUNT(*) FROM inventario.movimiento
                    WHERE id_movimiento>%s AND tipo_movimiento='BAJA_DAÑO'
                      AND id_bodega=%s AND id_producto=%s AND num_lote IS NULL
                    """,
                    (mov_max0, id_bod_np, id_prod_np),
                )
                nm = cur.fetchone()[0]
            if wins == 1 and st == 10 and nm == 1:
                ok("CONC_A uno gana stock=10 un_movimiento")
            else:
                fail("CONC_A", f"wins={wins} stock={st} movs={nm}")

            log("=== CONC B: dos bajas mismo lote perecedero ===")
            with admin_conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO inventario.lote (
                      num_lote,id_bodega,id_producto,id_proveedor,
                      fech_vencimiento,cantidad_recibida,cantidad_actual
                    ) VALUES (99710,%s,%s,%s,CURRENT_DATE+20,16,16)
                    ON CONFLICT (num_lote,id_bodega,id_producto)
                    DO UPDATE SET cantidad_actual=16
                    """,
                    (id_bodega, id_prod_per, id_prov),
                )
                cur.execute(
                    """
                    UPDATE inventario.inventario SET cantidad_actual=16
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (id_bodega, id_prod_per),
                )
            admin_conn.commit()

            def w_per(conn, lbl):
                with conn.cursor() as c:
                    call_dano_per(c, id_bodega, id_prod_per, 99710, 10, id_admin, f"C B {lbl}")

            s1, s2 = run_two(props, "B", lambda c, l: w_per(c, l))
            log(f"CONC_B S1 ok={s1.ok}; S2 ok={s2.ok}")
            with admin_conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT cantidad_actual FROM inventario.lote
                    WHERE num_lote=99710 AND id_bodega=%s AND id_producto=%s
                    """,
                    (id_bodega, id_prod_per),
                )
                lt = cur.fetchone()[0]
                cur.execute(
                    "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                    (id_bodega, id_prod_per),
                )
                inv = cur.fetchone()[0]
            if sum(1 for s in (s1, s2) if s.ok) == 1 and lt == 6 and inv == 6 and lt >= 0:
                ok("CONC_B lote e inventario=6")
            else:
                fail("CONC_B", f"lote={lt} inv={inv}")

            log("=== CONC C: daño vs vencimiento mismo lote vencido ===")
            with admin_conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO inventario.lote (
                      num_lote,id_bodega,id_producto,id_proveedor,
                      fech_vencimiento,cantidad_recibida,cantidad_actual
                    ) VALUES (99711,%s,%s,%s,CURRENT_DATE-1,12,12)
                    ON CONFLICT (num_lote, id_bodega, id_producto)
                    DO UPDATE SET cantidad_actual=12, fech_vencimiento=CURRENT_DATE-1
                    """,
                    (id_bodega, id_prod_per, id_prov),
                )
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=12 WHERE id_bodega=%s AND id_producto=%s",
                    (id_bodega, id_prod_per),
                )
            admin_conn.commit()

            def w_c1(conn, _lbl):
                with conn.cursor() as c:
                    call_dano_per(c, id_bodega, id_prod_per, 99711, 8, id_admin, "Conc C daño")

            def w_c2(conn, _lbl):
                with conn.cursor() as c:
                    call_venc(c, id_bodega, id_prod_per, 99711, 8, id_admin, "Conc C venc")

            barrier = threading.Barrier(2)
            outs: list[WorkerOutcome] = []
            lock = threading.Lock()

            def wrap(fn, lbl):
                c = connect(props)
                c.autocommit = False
                try:
                    barrier.wait(60)
                    fn(c, lbl)
                    c.commit()
                    with lock:
                        outs.append(WorkerOutcome(lbl, True, "ok"))
                except Exception as e:
                    c.rollback()
                    with lock:
                        outs.append(WorkerOutcome(lbl, False, str(e)))
                finally:
                    c.close()

            t1 = threading.Thread(target=wrap, args=(w_c1, "S1"))
            t2 = threading.Thread(target=wrap, args=(w_c2, "S2"))
            t1.start()
            t2.start()
            t1.join(120)
            t2.join(120)
            with admin_conn.cursor() as cur:
                cur.execute(
                    "SELECT cantidad_actual FROM inventario.lote WHERE num_lote=99711 AND id_bodega=%s AND id_producto=%s",
                    (id_bodega, id_prod_per),
                )
                lt = cur.fetchone()[0]
            log(f"CONC_C outcomes={outs} lote_restante={lt}")
            if sum(1 for o in outs if o.ok) == 1 and lt == 4:
                ok("CONC_C exclusión mutua lote=4")
            else:
                fail("CONC_C", f"outs={outs} lote={lt}")

            log("=== CONC D: suma > existencias ===")
            with admin_conn.cursor() as cur:
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=12 WHERE id_bodega=%s AND id_producto=%s",
                    (id_bod_np, id_prod_np),
                )
            admin_conn.commit()

            def w_d(conn, qty):
                with conn.cursor() as c:
                    call_np(c, id_bod_np, id_prod_np, qty, id_admin, "Conc D")

            s1, s2 = run_two(
                props,
                "D",
                lambda c, lbl: w_d(c, 8 if lbl == "S1" else 8),
            )
            with admin_conn.cursor() as cur:
                cur.execute(
                    "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
                    (id_bod_np, id_prod_np),
                )
                st = cur.fetchone()[0]
            log(f"CONC_D S1={s1.ok} S2={s2.ok} stock={st}")
            if sum(1 for s in (s1, s2) if s.ok) <= 1 and st >= 0:
                ok("CONC_D sin doble descuento ni negativos")
            else:
                fail("CONC_D", f"stock={st}")

            # Restauración (movimientos antes que lotes por FK)
            log("=== RESTAURACIÓN ===")
            test_lots = (99710, 99711)
            with admin_conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id_movimiento FROM inventario.movimiento
                    WHERE id_movimiento > %s
                      AND tipo_movimiento IN ('BAJA_DAÑO','BAJA_VENCIMIENTO')
                    """,
                    (mov_max0,),
                )
                mov_ids = [r[0] for r in cur.fetchall()]
                cur.execute(
                    """
                    DELETE FROM inventario.bitacora
                    WHERE id_bitacora > %s
                      AND tipo_accion IN ('BAJA_DAÑO','BAJA_VENCIMIENTO')
                    """,
                    (bit_max0,),
                )
                cur.execute(
                    """
                    DELETE FROM inventario.movimiento
                    WHERE id_movimiento > %s
                      AND tipo_movimiento IN ('BAJA_DAÑO','BAJA_VENCIMIENTO')
                    """,
                    (mov_max0,),
                )
                cur.execute(
                    """
                    DELETE FROM inventario.movimiento
                    WHERE num_lote = ANY(%s)
                      AND id_bodega=%s AND id_producto=%s
                    """,
                    (list(test_lots), id_bodega, id_prod_per),
                )
                cur.execute(
                    """
                    UPDATE inventario.inventario SET cantidad_actual=%s
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (np_inv_orig, id_bod_np, id_prod_np),
                )
                for lot in test_lots:
                    cur.execute(
                        """
                        DELETE FROM inventario.lote
                        WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s
                        """,
                        (lot, id_bodega, id_prod_per),
                    )
                cur.execute(
                    "SELECT COUNT(*) FROM inventario.movimiento WHERE id_movimiento > %s AND tipo_movimiento IN ('BAJA_DAÑO','BAJA_VENCIMIENTO') AND id_bodega IN (%s,%s)",
                    (mov_max0, id_bod_np, id_bodega),
                )
                resid_mov = cur.fetchone()[0]
                cur.execute(
                    "SELECT COUNT(*) FROM inventario.bitacora WHERE id_bitacora > %s AND tipo_accion IN ('BAJA_DAÑO','BAJA_VENCIMIENTO')",
                    (bit_max0,),
                )
                resid_bit = cur.fetchone()[0]
            admin_conn.commit()
            log(f"RESTAURACION movs_conc_eliminados={len(mov_ids)} resid_mov={resid_mov} resid_bit={resid_bit} np_stock={np_inv_orig}")
            if resid_mov == 0 and resid_bit == 0:
                ok("RESTAURACION_sin_residuos_conc")
            else:
                fail("RESTAURACION", f"resid mov={resid_mov} bit={resid_bit}")

        except Exception as exc:
            admin_conn.rollback()
            fail("CONCURRENCIA_SUITE", str(exc))
        finally:
            if mov_max0:
                try:
                    with admin_conn.cursor() as cur:
                        cur.execute(
                            """
                            DELETE FROM inventario.bitacora
                            WHERE id_bitacora > %s
                              AND tipo_accion IN ('BAJA_DAÑO','BAJA_VENCIMIENTO')
                            """,
                            (bit_max0,),
                        )
                        cur.execute(
                            """
                            DELETE FROM inventario.movimiento
                            WHERE id_movimiento > %s
                              AND tipo_movimiento IN ('BAJA_DAÑO','BAJA_VENCIMIENTO')
                            """,
                            (mov_max0,),
                        )
                        cur.execute(
                            """
                            DELETE FROM inventario.movimiento
                            WHERE num_lote = ANY(%s)
                              AND id_bodega=%s AND id_producto=%s
                            """,
                            ([99710, 99711], id_bodega, id_prod_per),
                        )
                        if np_inv_orig:
                            cur.execute(
                                """
                                UPDATE inventario.inventario SET cantidad_actual=%s
                                WHERE id_bodega=%s AND id_producto=%s
                                """,
                                (np_inv_orig, id_bod_np, id_prod_np),
                            )
                        for lot in (99710, 99711):
                            cur.execute(
                                """
                                DELETE FROM inventario.lote
                                WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s
                                """,
                                (lot, id_bodega, id_prod_per),
                            )
                    admin_conn.commit()
                    log("RESTAURACION finally ejecutada tras concurrencia")
                except Exception as rest_exc:
                    log(f"FAIL RESTAURACION_finally: {rest_exc}")
            admin_conn.close()

    log(f"=== RESUMEN failed={failed} incidencias={len(incidencias)} ===")
    for inc in incidencias:
        log(f"INCIDENCIA: {inc}")

    OUT.write_text("\n".join(lines) + f"\nfailed={failed}\n", encoding="utf-8")
    print(f"written {OUT} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
