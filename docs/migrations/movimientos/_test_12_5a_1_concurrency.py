"""Prueba de concurrencia sp_salida_* — preparación aislada con restauración."""
from __future__ import annotations

import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path

try:
    import psycopg2
    from psycopg2 import errors as pg_errors
except ImportError:
    sys.stderr.write("pip install psycopg2-binary\n")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[3]
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_test_12_5a_1_output.txt"


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


@dataclass
class WorkerOutcome:
    label: str
    ok: bool
    detail: str
    wait_ms: float


def call_salida_np(conn, bodega, producto, cantidad, usuario):
    with conn.cursor() as cur:
        cur.execute(
            "CALL inventario.sp_salida_no_perecedero(%s,%s,%s,%s,NULL)",
            (bodega, producto, cantidad, usuario),
        )
        if cur.description:
            row = cur.fetchone()
            return row[0] if row else None
    return None


def call_salida_per(conn, bodega, producto, cantidad, usuario):
    with conn.cursor() as cur:
        cur.execute(
            "CALL inventario.sp_salida_perecedero(%s,%s,%s,%s,NULL)",
            (bodega, producto, cantidad, usuario),
        )
        if cur.description:
            row = cur.fetchone()
            return row[0] if row else None
    return None


def run_concurrent_pair(
    props,
    bodega: int,
    producto: int,
    cantidad: int,
    usuario: int,
    perecedero: bool,
) -> tuple[WorkerOutcome, WorkerOutcome]:
    barrier = threading.Barrier(2)
    outcomes: list[WorkerOutcome] = []
    lock = threading.Lock()

    def worker(label: str):
        t0 = time.perf_counter()
        conn = connect(props)
        conn.autocommit = False
        try:
            barrier.wait(timeout=30)
            t1 = time.perf_counter()
            if perecedero:
                mov = call_salida_per(conn, bodega, producto, cantidad, usuario)
            else:
                mov = call_salida_np(conn, bodega, producto, cantidad, usuario)
            conn.commit()
            with lock:
                outcomes.append(
                    WorkerOutcome(
                        label,
                        True,
                        f"id_movimiento={mov}",
                        (t1 - t0) * 1000,
                    )
                )
        except Exception as exc:
            conn.rollback()
            with lock:
                outcomes.append(
                    WorkerOutcome(label, False, f"{type(exc).__name__}: {exc}", 0.0)
                )
        finally:
            conn.close()

    t1 = threading.Thread(target=worker, args=("S1",))
    t2 = threading.Thread(target=worker, args=("S2",))
    t1.start()
    t2.start()
    t1.join(timeout=120)
    t2.join(timeout=120)
    by = {o.label: o for o in outcomes}
    return by["S1"], by["S2"]


def main() -> int:
    props = load_props(PROPS)
    lines: list[str] = []
    failed = 0

    def log(msg: str) -> None:
        lines.append(msg)

    admin = connect(props)
    admin.autocommit = False
    try:
        with admin.cursor() as cur:
            cur.execute(
                """
                SELECT u.id_usuario
                FROM inventario.usuario u
                WHERE u.activo = TRUE AND NOT u.bloqueado
                LIMIT 1
                """
            )
            usuario = cur.fetchone()[0]

            cur.execute(
                """
                SELECT p.id_producto, i.id_bodega, i.cantidad_actual
                FROM inventario.no_perecedero np
                JOIN inventario.producto p ON p.id_producto = np.id_producto
                JOIN inventario.inventario i ON i.id_producto = p.id_producto
                LIMIT 1
                """
            )
            np = cur.fetchone()
            if not np:
                log("ABORT: sin producto no perecedero para prueba")
                admin.rollback()
                OUT.write_text("\n".join(lines), encoding="utf-8")
                return 2

            id_producto_np, id_bodega_np, qty_orig_np = np
            cur.execute(
                "SELECT COALESCE(MAX(id_movimiento),0) FROM inventario.movimiento"
            )
            mov_max_before_a = cur.fetchone()[0]

            log("=== Escenario A: no perecedero 30 / dos salidas de 20 ===")
            log(f"Producto={id_producto_np} bodega={id_bodega_np} stock_origen={qty_orig_np}")

            cur.execute(
                """
                UPDATE inventario.inventario
                SET cantidad_actual = 30
                WHERE id_bodega = %s AND id_producto = %s
                """,
                (id_bodega_np, id_producto_np),
            )
            admin.commit()

            w1, w2 = run_concurrent_pair(
                props, id_bodega_np, id_producto_np, 20, usuario, False
            )
            log(f"S1 ok={w1.ok} detail={w1.detail}")
            log(f"S2 ok={w2.ok} detail={w2.detail}")

            with admin.cursor() as cur2:
                cur2.execute(
                    """
                    SELECT cantidad_actual FROM inventario.inventario
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (id_bodega_np, id_producto_np),
                )
                stock_after_a = cur2.fetchone()[0]
                cur2.execute(
                    """
                    SELECT id_movimiento, cantidad FROM inventario.movimiento
                    WHERE tipo_movimiento='SALIDA' AND id_movimiento > %s
                      AND id_bodega=%s AND id_producto=%s
                    ORDER BY id_movimiento
                    """,
                    (mov_max_before_a, id_bodega_np, id_producto_np),
                )
                movs_a = cur2.fetchall()

            wins = sum(1 for w in (w1, w2) if w.ok)
            if wins != 1 or stock_after_a != 10 or sum(m[1] for m in movs_a) != 20:
                log(
                    f"FAIL A: wins={wins} stock={stock_after_a} movs={movs_a}"
                )
                failed += 1
            else:
                log(f"OK A stock_final={stock_after_a} movimientos_nuevos={movs_a}")

            with admin.cursor() as cur2:
                cur2.execute(
                    """
                    DELETE FROM inventario.movimiento
                    WHERE id_movimiento > %s AND tipo_movimiento='SALIDA'
                      AND id_bodega=%s AND id_producto=%s
                    """,
                    (mov_max_before_a, id_bodega_np, id_producto_np),
                )
                cur2.execute(
                    """
                    UPDATE inventario.inventario
                    SET cantidad_actual = %s
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (qty_orig_np, id_bodega_np, id_producto_np),
                )
            admin.commit()
            log(f"Restaurado no perecedero stock={qty_orig_np}")

            cur.execute(
                """
                SELECT p.id_producto FROM inventario.perecedero pe
                JOIN inventario.producto p ON p.id_producto = pe.id_producto
                LIMIT 1
                """
            )
            prod_per = cur.fetchone()[0]
            cur.execute(
                "SELECT id_bodega FROM inventario.bodega ORDER BY id_bodega LIMIT 1"
            )
            bodega_per = cur.fetchone()[0]
            cur.execute(
                "SELECT id_proveedor FROM inventario.proveedor ORDER BY id_proveedor LIMIT 1"
            )
            prov = cur.fetchone()[0]

            cur.execute(
                "SELECT COALESCE(MAX(id_movimiento),0) FROM inventario.movimiento"
            )
            mov_max_before_b = cur.fetchone()[0]

            lotes_backup: list[tuple] = []
            cur.execute(
                """
                SELECT num_lote, cantidad_actual, fech_vencimiento
                FROM inventario.lote
                WHERE id_bodega=%s AND id_producto=%s
                """,
                (bodega_per, prod_per),
            )
            for row in cur.fetchall():
                lotes_backup.append(row)

            cur.execute(
                """
                SELECT cantidad_actual FROM inventario.inventario
                WHERE id_bodega=%s AND id_producto=%s
                """,
                (bodega_per, prod_per),
            )
            inv_row = cur.fetchone()
            qty_orig_per = inv_row[0] if inv_row else 0

            log("=== Escenario B: perecedero FEFO concurrente ===")
            cur.execute(
                """
                UPDATE inventario.lote SET cantidad_actual = 0
                WHERE id_bodega=%s AND id_producto=%s
                  AND num_lote NOT IN (99820, 99821)
                """,
                (bodega_per, prod_per),
            )
            for num, venc, qty in ((99820, "2026-12-01", 18), (99821, "2027-02-01", 18)):
                cur.execute(
                    """
                    INSERT INTO inventario.lote (
                      num_lote, id_bodega, id_producto, id_proveedor,
                      fech_vencimiento, cantidad_recibida, cantidad_actual
                    ) VALUES (%s,%s,%s,%s,%s::date,%s,%s)
                    ON CONFLICT (num_lote, id_bodega, id_producto)
                    DO UPDATE SET cantidad_actual=%s, fech_vencimiento=EXCLUDED.fech_vencimiento
                    """,
                    (num, bodega_per, prod_per, prov, venc, qty, qty, qty),
                )
            cur.execute(
                """
                INSERT INTO inventario.inventario (id_bodega, id_producto, cantidad_actual, stock_minimo)
                VALUES (%s,%s,36,0)
                ON CONFLICT (id_bodega, id_producto)
                DO UPDATE SET cantidad_actual = 36
                """,
                (bodega_per, prod_per),
            )
            admin.commit()

            w1, w2 = run_concurrent_pair(
                props, bodega_per, prod_per, 20, usuario, True
            )
            log(f"B S1 ok={w1.ok} detail={w1.detail}")
            log(f"B S2 ok={w2.ok} detail={w2.detail}")

            with admin.cursor() as cur2:
                cur2.execute(
                    """
                    SELECT cantidad_actual FROM inventario.inventario
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (bodega_per, prod_per),
                )
                stock_b = cur2.fetchone()[0]
                cur2.execute(
                    """
                    SELECT num_lote, cantidad_actual FROM inventario.lote
                    WHERE id_bodega=%s AND id_producto=%s AND num_lote IN (99820,99821)
                    ORDER BY num_lote
                    """,
                    (bodega_per, prod_per),
                )
                lotes_b = cur2.fetchall()
                cur2.execute(
                    """
                    SELECT id_movimiento, num_lote, cantidad FROM inventario.movimiento
                    WHERE tipo_movimiento='SALIDA' AND id_movimiento > %s
                      AND id_bodega=%s AND id_producto=%s
                    ORDER BY id_movimiento
                    """,
                    (mov_max_before_b, bodega_per, prod_per),
                )
                movs_b = cur2.fetchall()

            wins_b = sum(1 for w in (w1, w2) if w.ok)
            sum_lotes = sum(q for _, q in lotes_b)
            if wins_b != 1 or stock_b != 16 or sum_lotes != 16:
                log(f"FAIL B: wins={wins_b} inv={stock_b} lotes={lotes_b} movs={movs_b}")
                failed += 1
            else:
                log(f"OK B inv={stock_b} lotes={lotes_b} movs={movs_b}")

            with admin.cursor() as cur2:
                cur2.execute(
                    """
                    DELETE FROM inventario.movimiento
                    WHERE id_movimiento > %s AND tipo_movimiento='SALIDA'
                      AND id_bodega=%s AND id_producto=%s
                    """,
                    (mov_max_before_b, bodega_per, prod_per),
                )
                cur2.execute(
                    """
                    DELETE FROM inventario.lote
                    WHERE id_bodega=%s AND id_producto=%s AND num_lote IN (99820, 99821)
                    """,
                    (bodega_per, prod_per),
                )
                for num_lote, cant, fv in lotes_backup:
                    cur2.execute(
                        """
                        UPDATE inventario.lote
                        SET cantidad_actual=%s, fech_vencimiento=%s
                        WHERE num_lote=%s AND id_bodega=%s AND id_producto=%s
                        """,
                        (cant, fv, num_lote, bodega_per, prod_per),
                    )
                if qty_orig_per:
                    cur2.execute(
                        """
                        UPDATE inventario.inventario
                        SET cantidad_actual=%s
                        WHERE id_bodega=%s AND id_producto=%s
                        """,
                        (qty_orig_per, bodega_per, prod_per),
                    )
            admin.commit()

            log("=== Escenario C: rollback ante error (misma conexión) ===")
            cur.execute(
                """
                UPDATE inventario.inventario
                SET cantidad_actual = 30
                WHERE id_bodega=%s AND id_producto=%s
                """,
                (id_bodega_np, id_producto_np),
            )
            admin.commit()
            mov_max_c = mov_max_before_a
            cur.execute(
                "SELECT COALESCE(MAX(id_movimiento),0) FROM inventario.movimiento"
            )
            mov_max_c = cur.fetchone()[0]

            try:
                with admin.cursor() as cur2:
                    call_salida_np(admin, id_bodega_np, id_producto_np, 50, usuario)
                admin.commit()
                log("FAIL C: debió fallar con cantidad 50")
                failed += 1
            except Exception as exc:
                admin.rollback()
                log(f"OK C rechazo esperado: {type(exc).__name__}")

            with admin.cursor() as cur2:
                cur2.execute(
                    """
                    SELECT cantidad_actual FROM inventario.inventario
                    WHERE id_bodega=%s AND id_producto=%s
                    """,
                    (id_bodega_np, id_producto_np),
                )
                stock_c = cur2.fetchone()[0]
                cur2.execute(
                    """
                    SELECT COUNT(*) FROM inventario.movimiento
                    WHERE id_movimiento > %s AND tipo_movimiento='SALIDA'
                      AND id_bodega=%s AND id_producto=%s
                    """,
                    (mov_max_c, id_bodega_np, id_producto_np),
                )
                nuevos_c = cur2.fetchone()[0]
            if stock_c != 30 or nuevos_c != 0:
                log(f"FAIL C stock={stock_c} movimientos_nuevos={nuevos_c}")
                failed += 1
            else:
                log(f"OK C stock={stock_c} sin movimientos nuevos")

            cur.execute(
                """
                UPDATE inventario.inventario SET cantidad_actual=%s
                WHERE id_bodega=%s AND id_producto=%s
                """,
                (qty_orig_np, id_bodega_np, id_producto_np),
            )
            admin.commit()

            conn_lock = connect(props)
            conn_lock.autocommit = True
            with conn_lock.cursor() as c:
                c.execute(
                    """
                    SELECT pg_get_functiondef(p.oid)
                    FROM pg_proc p
                    JOIN pg_namespace n ON n.oid=p.pronamespace
                    WHERE n.nspname='inventario' AND p.proname='sp_salida_no_perecedero'
                    """
                )
                defn = c.fetchone()[0]
                tiene_bodega = "FROM bodega" in defn and "FOR UPDATE" in defn
                tiene_inv = "FROM inventario" in defn and "FOR UPDATE" in defn
                log(
                    f"Evidencia código desplegado: bodega_FOR_UPDATE={tiene_bodega} "
                    f"inventario_FOR_UPDATE={tiene_inv}"
                )
            conn_lock.close()

    except Exception as exc:
        admin.rollback()
        log(f"ERROR fatal: {type(exc).__name__}: {exc}")
        failed += 1
    finally:
        admin.close()

    lines.append(f"failed={failed}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
