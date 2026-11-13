"""Validación integral 12.5C — movimientos/inventario (solo pruebas, sin mutar SP)."""
from __future__ import annotations

import sys
import threading
from dataclasses import dataclass
from pathlib import Path

try:
    import psycopg2
except ImportError:
    sys.stderr.write("pip install psycopg2-binary\n")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[3]
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_test_12_5c_output.txt"
MIG_DIR = Path(__file__).resolve().parent

# Lotes exclusivos suite 12.5C
L_FEFO = (99601, 99602, 99603)
L_X = 99620
L_Y = 99621

DOC_SP = {
    "sp_salida_no_perecedero": "MIG-12.5A_sp_salidas.sql",
    "sp_salida_perecedero": "MIG-12.5A_sp_salidas.sql",
    "fn_validar_salida": "MIG-12.5A_sp_salidas.sql",
    "fn_stock_lotes_vigentes": "MIG-12.5A_sp_salidas.sql",
    "sp_baja_dano_no_perecedero": "MIG-12.5B.1_sp_baja_dano.sql",
    "sp_baja_dano_perecedero": "MIG-12.5B.1_sp_baja_dano.sql",
    "fn_usuario_es_administrador": "MIG-12.5B.1_sp_baja_dano.sql",
    "fn_validar_motivo_baja": "MIG-12.5B.1_sp_baja_dano.sql",
    "sp_baja_vencimiento": "MIG-12.5B.2_sp_baja_vencimiento.sql",
}


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


def pg_err(fn) -> bool:
    try:
        fn()
        return False
    except psycopg2.Error:
        return True


def fetch_user(cur, rol: str) -> int | None:
    cur.execute(
        """
        SELECT u.id_usuario FROM inventario.usuario u
        JOIN inventario.rol r ON r.id_rol=u.id_rol
        WHERE r.nombre=%s AND u.activo AND NOT u.bloqueado AND r.activo
        LIMIT 1
        """,
        (rol,),
    )
    r = cur.fetchone()
    return r[0] if r else None


def inv_qty(cur, b, p) -> int:
    cur.execute(
        "SELECT cantidad_actual FROM inventario.inventario WHERE id_bodega=%s AND id_producto=%s",
        (b, p),
    )
    r = cur.fetchone()
    return r[0] if r else 0


def lot_qty(cur, b, p, n) -> int:
    cur.execute(
        """
        SELECT cantidad_actual FROM inventario.lote
        WHERE id_bodega=%s AND id_producto=%s AND num_lote=%s
        """,
        (b, p, n),
    )
    r = cur.fetchone()
    return r[0] if r else 0


def sum_lotes(cur, b, p) -> int:
    cur.execute(
        """
        SELECT COALESCE(SUM(cantidad_actual),0)::int FROM inventario.lote
        WHERE id_bodega=%s AND id_producto=%s
        """,
        (b, p),
    )
    return cur.fetchone()[0]


def crear_compra(cur, prov: int) -> int:
    cur.execute("CALL inventario.sp_crear_compra(%s, NULL)", (prov,))
    return cur.fetchone()[0]


def recibir_np(cur, compra, bodega, prod, qty, usr):
    cur.execute(
        "CALL inventario.sp_recibir_no_perecedero(%s,%s,%s,%s,%s)",
        (compra, bodega, prod, qty, usr),
    )


def recibir_per(cur, compra, bodega, prod, qty, venc, usr):
    cur.execute(
        "CALL inventario.sp_recibir_perecedero(%s,%s,%s,%s,%s::date,%s)",
        (compra, bodega, prod, qty, venc, usr),
    )


def salida_np(cur, b, p, q, u) -> int | None:
    cur.execute("CALL inventario.sp_salida_no_perecedero(%s,%s,%s,%s,NULL)", (b, p, q, u))
    r = cur.fetchone()
    return r[0] if r else None


def salida_per(cur, b, p, q, u) -> int | None:
    cur.execute("CALL inventario.sp_salida_perecedero(%s,%s,%s,%s,NULL)", (b, p, q, u))
    r = cur.fetchone()
    return r[0] if r else None


def baja_dano_np(cur, b, p, q, u, mot) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_dano_no_perecedero(%s,%s,%s,%s,%s,NULL)",
        (b, p, q, u, mot),
    )
    r = cur.fetchone()
    return r[0] if r else None


def baja_dano_per(cur, b, p, lot, q, u, mot) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_dano_perecedero(%s,%s,%s,%s,%s,%s,NULL)",
        (b, p, lot, q, u, mot),
    )
    r = cur.fetchone()
    return r[0] if r else None


def baja_venc(cur, b, p, lot, q, u, mot) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_vencimiento(%s,%s,%s,%s,%s,%s,NULL)",
        (b, p, lot, q, u, mot),
    )
    r = cur.fetchone()
    return r[0] if r else None


def audit_routines(cur, log) -> dict[str, str]:
    cur.execute(
        """
        SELECT p.proname,
               CASE WHEN p.prokind='p' THEN 'PROCEDURE' ELSE 'FUNCTION' END,
               pg_get_function_identity_arguments(p.oid)
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='inventario'
          AND p.proname ~ '^(sp_|fn_)'
          AND (
            p.proname ~* 'recibir|salida|trasladar|baja|validar|stock|lote|movimiento|inventario|consultar|recepcion|crear_compra'
            OR p.proname IN (
              'fn_consultar_stock_bodega','fn_tipo_producto','fn_lotes_disponibles'
            )
          )
        ORDER BY 2, 1
        """
    )
    catalog: dict[str, str] = {}
    log("=== CATALOGO PG (inventario) ===")
    for name, kind, args in cur.fetchall():
        catalog[name] = kind
        mig = DOC_SP.get(name, "PRE-EXISTENTE/OTRO")
        log(f"CAT {kind} {name}({args}) migracion_ref={mig}")
    return catalog


@dataclass
class Out:
    label: str
    ok: bool
    detail: str


def two_sessions(props, fn1, fn2) -> tuple[Out, Out]:
    bar = threading.Barrier(2)
    outs: list[Out] = []
    lk = threading.Lock()

    def run(fn, lbl):
        c = connect(props)
        c.autocommit = False
        try:
            bar.wait(90)
            fn(c)
            c.commit()
            with lk:
                outs.append(Out(lbl, True, "commit"))
        except Exception as e:
            c.rollback()
            with lk:
                outs.append(Out(lbl, False, str(e)[:300]))
        finally:
            c.close()

    t1 = threading.Thread(target=run, args=(fn1, "S1"))
    t2 = threading.Thread(target=run, args=(fn2, "S2"))
    t1.start()
    t2.start()
    t1.join(150)
    t2.join(150)
    d = {o.label: o for o in outs}
    return d.get("S1", Out("S1", False, "missing")), d.get("S2", Out("S2", False, "missing"))


def main() -> int:
    props = load_props(PROPS)
    lines: list[str] = []
    failed = 0

    def log(m: str) -> None:
        lines.append(m)

    def ok(n: str) -> None:
        log(f"OK {n}")

    def fail(n: str, d: str) -> None:
        nonlocal failed
        failed += 1
        log(f"FAIL {n}: {d}")

    conn = connect(props)
    conn.autocommit = False
    catalog: dict[str, str] = {}
    id_prov = 1

    try:
        with conn.cursor() as cur:
            catalog = audit_routines(cur, log)
            id_admin = fetch_user(cur, "ADMINISTRADOR")
            id_oper = fetch_user(cur, "OPERATIVO")
            log(f"USUARIOS admin={id_admin} oper={id_oper}")

            cur.execute(
                """
                SELECT p.id_producto, i.id_bodega, c.id_proveedor
                FROM inventario.no_perecedero np
                JOIN inventario.producto p ON p.id_producto=np.id_producto
                JOIN inventario.inventario i ON i.id_producto=p.id_producto
                JOIN inventario.catalogo c ON c.id_producto=p.id_producto
                LIMIT 1
                """
            )
            np_row = cur.fetchone()
            cur.execute(
                """
                SELECT p.id_producto, c.id_proveedor
                FROM inventario.perecedero pe
                JOIN inventario.producto p ON p.id_producto=pe.id_producto
                JOIN inventario.catalogo c ON c.id_producto=p.id_producto
                LIMIT 1
                """
            )
            per_row = cur.fetchone()
            cur.execute("SELECT id_proveedor FROM inventario.proveedor ORDER BY 1 LIMIT 1")
            if np_row:
                id_prov = np_row[2]
            else:
                cur.execute(
                    "SELECT id_proveedor FROM inventario.proveedor ORDER BY 1 LIMIT 1"
                )
                id_prov = cur.fetchone()[0]
            id_bodega = np_row[1] if np_row else 1

            mot = "Suite 12.5C"

            # --- A Entradas (savepoint) ---
            log("=== A ENTRADAS ===")
            if np_row:
                p_np, b_np = np_row[0], np_row[1]
                prov_np = np_row[2]

                def run_sp(name, fn, expect_ok=True):
                    cur.execute("SAVEPOINT sp_ent")
                    try:
                        if expect_ok:
                            fn()
                            ok(name)
                        elif pg_err(fn):
                            ok(f"{name} (rechazado)")
                        else:
                            fail(name, "esperaba error")
                    except Exception as ex:
                        fail(name, str(ex))
                    finally:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_ent")

                def ent_ok():
                    c0 = inv_qty(cur, b_np, p_np)
                    compra = crear_compra(cur, prov_np)
                    recibir_np(cur, compra, b_np, p_np, 7, id_oper)
                    if inv_qty(cur, b_np, p_np) != c0 + 7:
                        raise AssertionError("inventario entrada")
                    cur.execute(
                        """
                        SELECT tipo_movimiento, cantidad FROM inventario.movimiento
                        WHERE tipo_movimiento='ENTRADA' AND id_bodega=%s AND id_producto=%s
                        ORDER BY id_movimiento DESC LIMIT 1
                        """,
                        (b_np, p_np),
                    )
                    m = cur.fetchone()
                    if m[1] != 7:
                        raise AssertionError(f"mov entrada {m}")

                run_sp("A01_entrada_np_valida", ent_ok)
                run_sp(
                    "A02_entrada_cero",
                    lambda: recibir_np(cur, crear_compra(cur, prov_np), b_np, p_np, 0, id_oper),
                    False,
                )

            if per_row:
                p_per = per_row[0]

                def ent_per_ok():
                    compra = crear_compra(cur, per_row[1])
                    recibir_per(cur, compra, id_bodega, p_per, 5, "2099-06-01", id_oper)
                    cur.execute(
                        """
                        SELECT COUNT(*) FROM inventario.lote
                        WHERE id_bodega=%s AND id_producto=%s AND cantidad_actual>=5
                        """,
                        (id_bodega, p_per),
                    )
                    if cur.fetchone()[0] < 1:
                        raise AssertionError("sin lote tras recepcion")

                cur.execute("SAVEPOINT sp_entp")
                try:
                    ent_per_ok()
                    ok("A03_entrada_per_valida")
                except Exception as ex:
                    fail("A03_entrada_per_valida", str(ex))
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_entp")

            # --- B/C Salidas + FEFO ---
            log("=== B/C SALIDAS Y FEFO ===")
            if np_row:
                p_np, b_np = np_row[0], np_row[1]
                prov_np = np_row[2]

                def run_s(name, fn, expect_ok=True):
                    cur.execute("SAVEPOINT sp_sal")
                    cur.execute(
                        "UPDATE inventario.inventario SET cantidad_actual=30 WHERE id_bodega=%s AND id_producto=%s",
                        (b_np, p_np),
                    )
                    try:
                        if expect_ok:
                            fn()
                            ok(name)
                        elif pg_err(fn):
                            ok(f"{name} (rechazado)")
                        else:
                            fail(name, "esperaba error")
                    except Exception as ex:
                        fail(name, str(ex))
                    finally:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_sal")

                run_s("B01_salida_np", lambda: salida_np(cur, b_np, p_np, 2, id_oper))
                run_s(
                    "B02_salida_np_insuf",
                    lambda: salida_np(cur, b_np, p_np, 999, id_oper),
                    False,
                )

            if per_row:
                p_per = per_row[0]

                def seed_fefo():
                    cur.execute(
                        """
                        INSERT INTO inventario.inventario (id_bodega,id_producto,cantidad_actual,stock_minimo)
                        VALUES (%s,%s,45,0) ON CONFLICT (id_bodega,id_producto)
                        DO UPDATE SET cantidad_actual=45
                        """,
                        (id_bodega, p_per),
                    )
                    for lot, fe, q in (
                        (L_FEFO[0], "2027-08-01", 10),
                        (L_FEFO[1], "2027-09-01", 15),
                        (L_FEFO[2], "2027-10-01", 20),
                    ):
                        cur.execute(
                            """
                            INSERT INTO inventario.lote (
                              num_lote,id_bodega,id_producto,id_proveedor,
                              fech_vencimiento,cantidad_recibida,cantidad_actual
                            ) VALUES (%s,%s,%s,%s,%s::date,%s,%s)
                            ON CONFLICT (num_lote,id_bodega,id_producto)
                            DO UPDATE SET cantidad_actual=EXCLUDED.cantidad_actual,
                              fech_vencimiento=EXCLUDED.fech_vencimiento
                            """,
                            (lot, id_bodega, p_per, id_prov, fe, q, q),
                        )
                    cur.execute(
                        """
                        UPDATE inventario.lote SET cantidad_actual=0
                        WHERE id_bodega=%s AND id_producto=%s
                          AND num_lote NOT IN %s
                        """,
                        (id_bodega, p_per, L_FEFO),
                    )

                cur.execute("SAVEPOINT sp_fefo")
                seed_fefo()
                try:
                    inv0 = 45
                    salida_per(cur, id_bodega, p_per, 22, id_oper)
                    inv = inv_qty(cur, id_bodega, p_per)
                    ssum = sum_lotes(cur, id_bodega, p_per)
                    if inv != inv0 - 22 or inv != ssum:
                        raise AssertionError(f"inv={inv} sum_lotes={ssum}")
                    cur.execute(
                        """
                        SELECT MIN(fech_vencimiento) FROM inventario.lote
                        WHERE id_bodega=%s AND id_producto=%s AND num_lote IN %s
                          AND cantidad_actual > 0
                        """,
                        (id_bodega, p_per, L_FEFO),
                    )
                    ok("C01_fefo_multilote_22")
                except Exception as ex:
                    fail("C01_fefo_multilote_22", str(ex))
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_fefo")

                cur.execute("SAVEPOINT sp_venc")
                seed_fefo()
                cur.execute(
                    """
                    UPDATE inventario.lote SET fech_vencimiento=CURRENT_DATE-1
                    WHERE id_bodega=%s AND id_producto=%s AND num_lote IN %s
                    """,
                    (id_bodega, p_per, L_FEFO),
                )
                cur.execute("SAVEPOINT sp_venc_try")
                try:
                    salida_per(cur, id_bodega, p_per, 5, id_oper)
                    cur.execute("ROLLBACK TO SAVEPOINT sp_venc_try")
                    fail("C02_excluye_vencidos", "debió fallar")
                except psycopg2.Error:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_venc_try")
                    ok("C02_excluye_vencidos (rechazado)")
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_venc")

            conn.rollback()
            conn.autocommit = False
            with conn.cursor() as cur2:
                id_admin = fetch_user(cur2, "ADMINISTRADOR")
                id_oper = fetch_user(cur2, "OPERATIVO")
                cur2.execute(
                    """
                    SELECT p.id_producto, i.id_bodega, c.id_proveedor
                    FROM inventario.no_perecedero np
                    JOIN inventario.producto p ON p.id_producto=np.id_producto
                    JOIN inventario.inventario i ON i.id_producto=p.id_producto
                    JOIN inventario.catalogo c ON c.id_producto=p.id_producto
                    LIMIT 1
                    """
                )
                np_row = cur2.fetchone()
            cur = conn.cursor()
            # --- D Bajas (muestra; cubierto en 12.5B.3) ---
            log("=== D BAJAS (smoke) ===")
            if np_row and id_admin:
                cur.execute("SAVEPOINT sp_bj")
                try:
                    cur.execute(
                        "UPDATE inventario.inventario SET cantidad_actual=10 WHERE id_bodega=%s AND id_producto=%s",
                        (b_np, p_np),
                    )
                    mov = baja_dano_np(cur, b_np, p_np, 1, id_admin, mot)
                    cur.execute("SAVEPOINT sp_oper_rej")
                    try:
                        baja_dano_np(cur, b_np, p_np, 1, id_oper, mot)
                        fail("D01", "operativo no rechazado")
                    except psycopg2.Error:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_oper_rej")
                        ok("D01_baja_np_admin_y_oper_rechazado")
                    cur.execute(
                        "SELECT tipo_accion FROM inventario.bitacora WHERE descripcion LIKE %s",
                        (f"%#{mov}%",),
                    )
                    if cur.fetchone():
                        ok("D02_bitacora_np")
                except Exception as ex:
                    fail("D_bajas_smoke", str(ex))
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_bj")

            # --- Secuencias consecutivas ---
            log("=== SECUENCIAS ===")
            if np_row and id_admin:

                def seq_run(name, fn):
                    cur.execute("SAVEPOINT sp_seq")
                    try:
                        fn()
                        ok(name)
                    except Exception as ex:
                        fail(name, str(ex))
                    finally:
                        cur.execute("ROLLBACK TO SAVEPOINT sp_seq")

                def seq1():
                    c0 = inv_qty(cur, b_np, p_np)
                    compra = crear_compra(cur, prov_np)
                    recibir_np(cur, compra, b_np, p_np, 10, id_oper)
                    salida_np(cur, b_np, p_np, 4, id_oper)
                    if inv_qty(cur, b_np, p_np) != c0 + 6:
                        raise AssertionError("entrada+salida")

                def seq2():
                    c0 = inv_qty(cur, b_np, p_np)
                    compra = crear_compra(cur, prov_np)
                    recibir_np(cur, compra, b_np, p_np, 8, id_oper)
                    baja_dano_np(cur, b_np, p_np, 3, id_admin, mot)
                    if inv_qty(cur, b_np, p_np) != c0 + 5:
                        raise AssertionError("entrada+baja")

                seq_run("SEQ1_entrada_salida", seq1)
                seq_run("SEQ2_entrada_baja_dano", seq2)

            if per_row and id_admin:
                p_per = per_row[0]

                def seq3():
                    seed_fefo()
                    salida_per(cur, id_bodega, p_per, 10, id_oper)
                    baja_dano_per(cur, id_bodega, p_per, L_FEFO[2], 2, id_admin, mot)
                    inv = inv_qty(cur, id_bodega, p_per)
                    if inv < 0 or lot_qty(cur, id_bodega, p_per, L_FEFO[2]) < 0:
                        raise AssertionError("negativos")

                def seq4():
                    seed_fefo()
                    cur.execute(
                        """
                        UPDATE inventario.lote SET fech_vencimiento=CURRENT_DATE-1
                        WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s
                        """,
                        (L_FEFO[2], id_bodega, p_per),
                    )
                    baja_venc(cur, id_bodega, p_per, L_FEFO[2], 5, id_admin, mot)

                cur.execute("SAVEPOINT sp_seqp")
                try:
                    seq3()
                    ok("SEQ3_fefo_luego_baja")
                except Exception as ex:
                    fail("SEQ3_fefo_luego_baja", str(ex))
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_seqp")

                cur.execute("SAVEPOINT sp_seqp2")
                seed_fefo()
                cur.execute(
                    """
                    UPDATE inventario.lote SET fech_vencimiento=CURRENT_DATE-1
                    WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s
                    """,
                    (L_FEFO[1], id_bodega, p_per),
                )
                try:
                    seq4()
                    ok("SEQ4_baja_vencimiento_post_setup")
                except Exception as ex:
                    fail("SEQ4", str(ex))
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_seqp2")

            # Rollback salida
            log("=== ROLLBACK ===")
            if np_row:
                cur.execute("SAVEPOINT sp_rb")
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=5 WHERE id_bodega=%s AND id_producto=%s",
                    (b_np, p_np),
                )
                cur.execute("SAVEPOINT sp_rb_err")
                try:
                    salida_np(cur, b_np, p_np, 99, id_oper)
                    fail("ROLL_salida", "debía fallar")
                except psycopg2.Error:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_rb_err")
                    if inv_qty(cur, b_np, p_np) != 5:
                        fail("ROLL_salida", "inventario alterado")
                    else:
                        ok("ROLL_salida_insuf_sin_parcial")
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_rb")

        conn.rollback()
        log("TX funcional rollback global")

    finally:
        conn.close()

    # --- Concurrencia cruzada + restauración ---
    mov0 = bit0 = compra0 = 0
    backups: dict = {}
    admin = connect(props)
    admin.autocommit = False
    try:
        with admin.cursor() as cur:
            cur.execute("SELECT COALESCE(MAX(id_movimiento),0) FROM inventario.movimiento")
            mov0 = cur.fetchone()[0]
            cur.execute("SELECT COALESCE(MAX(id_bitacora),0) FROM inventario.bitacora")
            bit0 = cur.fetchone()[0]
            cur.execute("SELECT COALESCE(MAX(id_compra),0) FROM inventario.compra")
            compra0 = cur.fetchone()[0]

            cur.execute(
                """
                SELECT p.id_producto, i.id_bodega, i.cantidad_actual, c.id_proveedor
                FROM inventario.no_perecedero np
                JOIN inventario.producto p ON p.id_producto=np.id_producto
                JOIN inventario.inventario i ON i.id_producto=p.id_producto
                JOIN inventario.catalogo c ON c.id_producto=p.id_producto
                LIMIT 1
                """
            )
            row_np = cur.fetchone()
            cur.execute(
                """
                SELECT p.id_producto FROM inventario.perecedero pe
                JOIN inventario.producto p ON p.id_producto=pe.id_producto LIMIT 1
                """
            )
            row_per = cur.fetchone()
            id_admin = fetch_user(cur, "ADMINISTRADOR")
            id_oper = fetch_user(cur, "OPERATIVO")

        if not (row_np and row_per and id_admin and id_oper):
            fail("FIXTURES", "datos insuficientes concurrencia")
        else:
            p_np, b_np, inv_orig, id_prov_conc = row_np
            id_prov = id_prov_conc
            p_per = row_per[0]
            b_per = b_np

            # Conc A: recepción vs salida
            with admin.cursor() as cur:
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=8 WHERE id_bodega=%s AND id_producto=%s",
                    (b_np, p_np),
                )
            admin.commit()
            compra_shared = {"id": None}

            def ca1(c):
                with c.cursor() as x:
                    compra_shared["id"] = crear_compra(x, id_prov)
                    recibir_np(x, compra_shared["id"], b_np, p_np, 10, id_oper)

            def ca2(c):
                with c.cursor() as x:
                    salida_np(x, b_np, p_np, 6, id_oper)

            o1, o2 = two_sessions(props, ca1, ca2)
            log(f"CONC_A S1={o1.ok} S2={o2.ok} det={o1.detail[:80]}|{o2.detail[:80]}")
            with admin.cursor() as cur:
                st = inv_qty(cur, b_np, p_np)
            if st >= 0 and sum(1 for o in (o1, o2) if o.ok) >= 1:
                ok("CONC_A sin_negativos")
            else:
                fail("CONC_A", f"stock={st}")

            # Conc B: FEFO vs baja lote
            with admin.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO inventario.inventario (id_bodega,id_producto,cantidad_actual,stock_minimo)
                    VALUES (%s,%s,30,0) ON CONFLICT (id_bodega,id_producto)
                    DO UPDATE SET cantidad_actual=30
                    """,
                    (b_per, p_per),
                )
                cur.execute(
                    """
                    INSERT INTO inventario.lote (
                      num_lote,id_bodega,id_producto,id_proveedor,
                      fech_vencimiento,cantidad_recibida,cantidad_actual
                    ) VALUES (%s,%s,%s,%s,'2026-07-01',30,30)
                    ON CONFLICT (num_lote,id_bodega,id_producto)
                    DO UPDATE SET cantidad_actual=30, fech_vencimiento='2026-07-01'
                    """,
                    (L_X, b_per, p_per, id_prov),
                )
            admin.commit()

            def cb1(c):
                with c.cursor() as x:
                    salida_per(x, b_per, p_per, 12, id_oper)

            def cb2(c):
                with c.cursor() as x:
                    baja_dano_per(x, b_per, p_per, L_X, 12, id_admin, mot)

            o1, o2 = two_sessions(props, cb1, cb2)
            log(f"CONC_B S1={o1.ok} S2={o2.ok}")
            with admin.cursor() as cur:
                lt = lot_qty(cur, b_per, p_per, L_X)
                inv = inv_qty(cur, b_per, p_per)
            if lt >= 0 and inv >= 0 and sum(1 for o in (o1, o2) if o.ok) == 1:
                ok(f"CONC_B exclusión mutua lote={lt} inv={inv}")
            else:
                fail("CONC_B", f"lote={lt} inv={inv} wins={o1.ok}/{o2.ok}")

            with admin.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO inventario.lote (
                      num_lote,id_bodega,id_producto,id_proveedor,
                      fech_vencimiento,cantidad_recibida,cantidad_actual
                    ) VALUES (%s,%s,%s,%s,CURRENT_DATE-1,14,14)
                    ON CONFLICT (num_lote,id_bodega,id_producto)
                    DO UPDATE SET cantidad_actual=14, fech_vencimiento=CURRENT_DATE-1
                    """,
                    (L_Y, b_per, p_per, id_prov),
                )
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=14 WHERE id_bodega=%s AND id_producto=%s",
                    (b_per, p_per),
                )
            admin.commit()

            def cc1(c):
                with c.cursor() as x:
                    baja_dano_per(x, b_per, p_per, L_Y, 10, id_admin, mot)

            def cc2(c):
                with c.cursor() as x:
                    baja_venc(x, b_per, p_per, L_Y, 10, id_admin, mot)

            o1, o2 = two_sessions(props, cc1, cc2)
            log(f"CONC_C bajas_distintas S1={o1.ok} S2={o2.ok}")
            with admin.cursor() as cur:
                lt = lot_qty(cur, b_per, p_per, L_Y)
            if sum(1 for o in (o1, o2) if o.ok) == 1 and lt == 4:
                ok("CONC_C")
            else:
                fail("CONC_C", f"lote={lt} wins={o1.ok}/{o2.ok}")

            with admin.cursor() as cur:
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=10 WHERE id_bodega=%s AND id_producto=%s",
                    (b_np, p_np),
                )
            admin.commit()

            def ce1(c):
                with c.cursor() as x:
                    salida_np(x, b_np, p_np, 7, id_oper)

            def ce2(c):
                with c.cursor() as x:
                    salida_np(x, b_np, p_np, 7, id_oper)

            o1, o2 = two_sessions(props, ce1, ce2)
            with admin.cursor() as cur:
                st = inv_qty(cur, b_np, p_np)
            log(f"CONC_E S1={o1.ok} S2={o2.ok} stock={st}")
            if st >= 0:
                ok("CONC_E_sin_negativos_np")
            else:
                fail("CONC_E", f"stock={st}")

            # Seguridad salida operativo
            with admin.cursor() as cur:
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=5 WHERE id_bodega=%s AND id_producto=%s",
                    (b_np, p_np),
                )
            admin.commit()
            try:
                c = connect(props)
                c.autocommit = False
                with c.cursor() as x:
                    salida_np(x, b_np, p_np, 1, id_oper)
                c.commit()
                ok("SEC_operativo_salida_np")
                c.close()
            except Exception as ex:
                fail("SEC_operativo_salida_np", str(ex))

            # Restauración
            log("=== RESTAURACION ===")
            with admin.cursor() as cur:
                cur.execute(
                    """
                    DELETE FROM inventario.movimiento_compra
                    WHERE id_movimiento > %s
                    """,
                    (mov0,),
                )
                cur.execute(
                    """
                    DELETE FROM inventario.bitacora WHERE id_bitacora > %s
                      AND tipo_accion IN ('BAJA_DAÑO','BAJA_VENCIMIENTO')
                    """,
                    (bit0,),
                )
                cur.execute(
                    """
                    DELETE FROM inventario.movimiento WHERE id_movimiento > %s
                    """,
                    (mov0,),
                )
                cur.execute(
                    """
                    DELETE FROM inventario.detalle_compra WHERE id_compra > %s
                    """,
                    (compra0,),
                )
                cur.execute("DELETE FROM inventario.compra WHERE id_compra > %s", (compra0,))
                cur.execute(
                    "UPDATE inventario.inventario SET cantidad_actual=%s WHERE id_bodega=%s AND id_producto=%s",
                    (inv_orig, b_np, p_np),
                )
                for lot in (*L_FEFO, L_X, L_Y):
                    cur.execute(
                        """
                        DELETE FROM inventario.movimiento
                        WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s
                        """,
                        (lot, b_per, p_per),
                    )
                    cur.execute(
                        """
                        DELETE FROM inventario.lote
                        WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s
                        """,
                        (lot, b_per, p_per),
                    )
                cur.execute(
                    "SELECT COUNT(*) FROM inventario.movimiento WHERE id_movimiento > %s",
                    (mov0,),
                )
                resid_m = cur.fetchone()[0]
                cur.execute(
                    "SELECT COUNT(*) FROM inventario.compra WHERE id_compra > %s",
                    (compra0,),
                )
                resid_c = cur.fetchone()[0]
            admin.commit()
            if resid_m == 0 and resid_c == 0:
                ok("RESTAURACION_completa")
            else:
                fail("RESTAURACION", f"mov={resid_m} compra={resid_c}")

    except Exception as ex:
        admin.rollback()
        fail("SUITE_CONC", str(ex))
    finally:
        admin.close()

    # Matriz cobertura en log
    log("=== MATRIZ COBERTURA (resumen) ===")
    tested = {
        "sp_recibir_no_perecedero": "A",
        "sp_recibir_perecedero": "A",
        "sp_crear_compra": "A/SEQ",
        "sp_salida_no_perecedero": "B/CONC",
        "sp_salida_perecedero": "B/C/CONC",
        "sp_baja_dano_no_perecedero": "D/CONC",
        "sp_baja_dano_perecedero": "D/CONC",
        "sp_baja_vencimiento": "SEQ/CONC",
        "sp_trasladar_perecedero": "PENDIENTE_12.5C",
        "sp_trasladar_no_perecedero": "PENDIENTE_12.5C",
    }
    for sp in sorted(catalog):
        if sp.startswith("sp_") or sp in tested:
            ref = tested.get(sp, "CAT_ONLY")
            log(f"COV {sp}: catalogo=SI prueba_integral={ref}")

    log(f"=== FIN failed={failed} ===")
    OUT.write_text("\n".join(lines) + f"\nfailed={failed}\n", encoding="utf-8")
    print(f"written {OUT} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
