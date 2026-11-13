"""Pruebas sp_baja_dano_* — savepoints; sin commit persistente de stock de prueba."""
from __future__ import annotations

import sys
from pathlib import Path

try:
    import psycopg2
except ImportError:
    sys.stderr.write("pip install psycopg2-binary\n")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[3]
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_test_12_5b_1_output.txt"

LOT_VIG = 99871
LOT_VEN = 99872


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


class Case:
    def __init__(self, name: str, fn, expect_ok: bool):
        self.name = name
        self.fn = fn
        self.expect_ok = expect_ok


def call_baja_np(cur, bodega, producto, cantidad, usuario, motivo) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_dano_no_perecedero(%s,%s,%s,%s,%s,NULL)",
        (bodega, producto, cantidad, usuario, motivo),
    )
    if cur.description:
        row = cur.fetchone()
        return row[0] if row else None
    return None


def call_baja_per(cur, bodega, producto, num_lote, cantidad, usuario, motivo) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_dano_perecedero(%s,%s,%s,%s,%s,%s,NULL)",
        (bodega, producto, num_lote, cantidad, usuario, motivo),
    )
    if cur.description:
        row = cur.fetchone()
        return row[0] if row else None
    return None


def expect_exception(fn) -> bool:
    try:
        fn()
        return False
    except psycopg2.Error:
        return True


def fetch_user(cur, rol_nombre: str) -> int | None:
    cur.execute(
        """
        SELECT u.id_usuario
        FROM inventario.usuario u
        JOIN inventario.rol r ON r.id_rol = u.id_rol
        WHERE r.nombre = %s AND u.activo = TRUE AND NOT u.bloqueado AND r.activo = TRUE
        ORDER BY u.id_usuario
        LIMIT 1
        """,
        (rol_nombre,),
    )
    row = cur.fetchone()
    return row[0] if row else None


def seed_isolated_perishables(cur, id_bodega: int, id_producto: int, id_proveedor: int) -> None:
    cur.execute(
        """
        INSERT INTO inventario.inventario (id_bodega, id_producto, cantidad_actual, stock_minimo)
        VALUES (%s, %s, 50, 0)
        ON CONFLICT (id_bodega, id_producto)
        DO UPDATE SET cantidad_actual = 50
        """,
        (id_bodega, id_producto),
    )
    cur.execute(
        """
        INSERT INTO inventario.lote (
            num_lote, id_bodega, id_producto, id_proveedor,
            fech_vencimiento, cantidad_recibida, cantidad_actual
        ) VALUES (%s,%s,%s,%s,CURRENT_DATE + 30,20,20)
        ON CONFLICT (num_lote, id_bodega, id_producto)
        DO UPDATE SET
            fech_vencimiento = EXCLUDED.fech_vencimiento,
            cantidad_actual = 20,
            cantidad_recibida = GREATEST(lote.cantidad_recibida, 20)
        """,
        (LOT_VIG, id_bodega, id_producto, id_proveedor),
    )
    cur.execute(
        """
        INSERT INTO inventario.lote (
            num_lote, id_bodega, id_producto, id_proveedor,
            fech_vencimiento, cantidad_recibida, cantidad_actual
        ) VALUES (%s,%s,%s,%s,CURRENT_DATE - 1,15,15)
        ON CONFLICT (num_lote, id_bodega, id_producto)
        DO UPDATE SET
            fech_vencimiento = EXCLUDED.fech_vencimiento,
            cantidad_actual = 15,
            cantidad_recibida = GREATEST(lote.cantidad_recibida, 15)
        """,
        (LOT_VEN, id_bodega, id_producto, id_proveedor),
    )


def stock_np(cur, bodega: int, producto: int) -> int:
    cur.execute(
        """
        SELECT cantidad_actual FROM inventario.inventario
        WHERE id_bodega=%s AND id_producto=%s
        """,
        (bodega, producto),
    )
    row = cur.fetchone()
    return row[0] if row else -1


def stock_lote(cur, bodega: int, producto: int, num_lote: int) -> int:
    cur.execute(
        """
        SELECT cantidad_actual FROM inventario.lote
        WHERE id_bodega=%s AND id_producto=%s AND num_lote=%s
        """,
        (bodega, producto, num_lote),
    )
    row = cur.fetchone()
    return row[0] if row else -1


def main() -> int:
    props = load_props(PROPS)
    conn = psycopg2.connect(
        jdbc_conninfo(
            props["spring.datasource.url"],
            props["spring.datasource.username"],
            props["spring.datasource.password"],
        )
    )
    lines: list[str] = []
    failed = 0

    def log(msg: str) -> None:
        lines.append(msg)

    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            id_admin = fetch_user(cur, "ADMINISTRADOR")
            id_oper = fetch_user(cur, "OPERATIVO")
            log(f"fixtures admin={id_admin} operativo={id_oper}")

            if id_admin is None or id_oper is None:
                log("FAIL: falta usuario ADMINISTRADOR u OPERATIVO")
                failed += 1
                OUT.write_text("\n".join(lines) + f"\nfailed={failed}\n", encoding="utf-8")
                conn.rollback()
                return 1

            cur.execute(
                """
                SELECT p.id_producto, i.id_bodega, i.cantidad_actual
                FROM inventario.no_perecedero np
                JOIN inventario.producto p ON p.id_producto = np.id_producto
                JOIN inventario.inventario i ON i.id_producto = p.id_producto
                WHERE i.cantidad_actual >= 5
                ORDER BY i.cantidad_actual DESC
                LIMIT 1
                """
            )
            np_row = cur.fetchone()

            cur.execute(
                """
                SELECT p.id_producto
                FROM inventario.perecedero pe
                JOIN inventario.producto p ON p.id_producto = pe.id_producto
                LIMIT 1
                """
            )
            per_prod = cur.fetchone()
            cur.execute(
                "SELECT id_bodega FROM inventario.bodega ORDER BY id_bodega LIMIT 1"
            )
            id_bodega = cur.fetchone()[0]
            cur.execute(
                "SELECT id_proveedor FROM inventario.proveedor ORDER BY id_proveedor LIMIT 1"
            )
            id_prov = cur.fetchone()[0]

            if not np_row or not per_prod:
                log("FAIL: sin fixtures de producto")
                failed += 1
            else:
                id_prod_np, id_bod_np, _ = np_row
                id_prod_per = per_prod[0]
                mot_ok = "Prueba automatizada 12.5B.1 daño"

                cases: list[Case] = []

                def run_cases():
                    nonlocal failed
                    for case in cases:
                        cur.execute("SAVEPOINT sp_case")
                        try:
                            if case.expect_ok:
                                case.fn()
                                log(f"OK {case.name}")
                            else:
                                if expect_exception(case.fn):
                                    log(f"OK {case.name} (rechazado)")
                                else:
                                    log(f"FAIL {case.name} (esperaba error)")
                                    failed += 1
                        except Exception as exc:
                            log(f"FAIL {case.name}: {type(exc).__name__}: {exc}")
                            failed += 1
                        finally:
                            cur.execute("ROLLBACK TO SAVEPOINT sp_case")

                # --- No perecedero ---
                if np_row:
                    bodega, producto = id_bod_np, id_prod_np

                    def t_np_ok():
                        before = stock_np(cur, bodega, producto)
                        mov = call_baja_np(cur, bodega, producto, 1, id_admin, mot_ok)
                        after = stock_np(cur, bodega, producto)
                        if mov is None or after != before - 1:
                            raise RuntimeError(f"stock {before}->{after} mov={mov}")
                        cur.execute(
                            """
                            SELECT tipo_movimiento, cantidad FROM inventario.movimiento
                            WHERE id_movimiento=%s
                            """,
                            (mov,),
                        )
                        m = cur.fetchone()
                        if m != ("BAJA_DAÑO", 1):
                            raise RuntimeError(f"movimiento {m}")
                        cur.execute(
                            """
                            SELECT tipo_accion, tabla_afectada FROM inventario.bitacora
                            WHERE id_usuario=%s AND tipo_accion='BAJA_DAÑO'
                            ORDER BY id_bitacora DESC LIMIT 1
                            """,
                            (id_admin,),
                        )
                        b = cur.fetchone()
                        if b != ("BAJA_DAÑO", "movimiento"):
                            raise RuntimeError(f"bitacora {b}")

                    cases.append(Case("01_np_baja_valida", t_np_ok, True))

                    def t_np_insuf():
                        call_baja_np(
                            cur, bodega, producto, 9_999_999, id_admin, mot_ok
                        )

                    cases.append(Case("03_np_stock_insuficiente", t_np_insuf, False))

                    def t_np_oper():
                        call_baja_np(cur, bodega, producto, 1, id_oper, mot_ok)

                    cases.append(Case("13_operativo_rechazado_np", t_np_oper, False))

                    def t_np_admin():
                        call_baja_np(cur, bodega, producto, 1, id_admin, mot_ok)

                    cases.append(Case("14_admin_aceptado_np", t_np_admin, True))

                def t_cero_np():
                    call_baja_np(cur, 1, 1, 0, id_admin, mot_ok)

                cases.append(Case("04_cantidad_cero", t_cero_np, False))

                def t_neg_np():
                    call_baja_np(cur, 1, 1, -3, id_admin, mot_ok)

                cases.append(Case("05_cantidad_negativa", t_neg_np, False))

                def t_prod_x():
                    call_baja_np(cur, id_bodega, -1, 1, id_admin, mot_ok)

                cases.append(Case("06_producto_inexistente", t_prod_x, False))

                def t_bod_x():
                    call_baja_np(cur, -1, 1, 1, id_admin, mot_ok)

                cases.append(Case("07_bodega_inexistente", t_bod_x, False))

                run_cases()
                cases.clear()

                # --- Perecedero con lotes aislados 99871/99872 ---
                cur.execute("SAVEPOINT sp_per_suite")
                try:
                    seed_isolated_perishables(cur, id_bodega, id_prod_per, id_prov)
                    bodega, producto = id_bodega, id_prod_per

                    def t_per_vig():
                        before_i = stock_np(cur, bodega, producto)
                        before_l = stock_lote(cur, bodega, producto, LOT_VIG)
                        mov = call_baja_per(
                            cur,
                            bodega,
                            producto,
                            LOT_VIG,
                            2,
                            id_admin,
                            mot_ok,
                        )
                        if stock_lote(cur, bodega, producto, LOT_VIG) != before_l - 2:
                            raise RuntimeError("lote vigente no descontado")
                        if stock_np(cur, bodega, producto) != before_i - 2:
                            raise RuntimeError("inventario no coherente")
                        cur.execute(
                            "SELECT tipo_movimiento, num_lote FROM inventario.movimiento WHERE id_movimiento=%s",
                            (mov,),
                        )
                        if cur.fetchone() != ("BAJA_DAÑO", LOT_VIG):
                            raise RuntimeError("movimiento perecedero vigente")

                    cases.append(Case("01_per_baja_valida_vigente", t_per_vig, True))
                    cases.append(Case("08_lote_vigente_dano", t_per_vig, True))

                    def t_per_ven():
                        before_l = stock_lote(cur, bodega, producto, LOT_VEN)
                        mov = call_baja_per(
                            cur,
                            bodega,
                            producto,
                            LOT_VEN,
                            1,
                            id_admin,
                            "Lote vencido prueba",
                        )
                        if stock_lote(cur, bodega, producto, LOT_VEN) != before_l - 1:
                            raise RuntimeError("lote vencido no descontado")
                        cur.execute(
                            "SELECT tipo_movimiento FROM inventario.movimiento WHERE id_movimiento=%s",
                            (mov,),
                        )
                        if cur.fetchone()[0] != "BAJA_DAÑO":
                            raise RuntimeError("tipo movimiento")

                    cases.append(Case("09_lote_vencido_dano", t_per_ven, True))

                    def t_per_oper():
                        call_baja_per(
                            cur,
                            bodega,
                            producto,
                            LOT_VIG,
                            1,
                            id_oper,
                            mot_ok,
                        )

                    cases.append(Case("13_operativo_rechazado_per", t_per_oper, False))

                    def t_per_motivo_bit():
                        mov = call_baja_per(
                            cur,
                            bodega,
                            producto,
                            LOT_VIG,
                            1,
                            id_admin,
                            "Motivo bitacora visible",
                        )
                        cur.execute(
                            """
                            SELECT descripcion FROM inventario.bitacora
                            WHERE tipo_accion='BAJA_DAÑO' AND descripcion LIKE %s
                            ORDER BY id_bitacora DESC LIMIT 1
                            """,
                            (f"%movimiento #{mov}%",),
                        )
                        desc = cur.fetchone()
                        if not desc or "Motivo bitacora visible" not in desc[0]:
                            raise RuntimeError(f"bitacora sin motivo: {desc}")

                    cases.append(Case("11_motivo_bitacora", t_per_motivo_bit, True))

                    def t_rollback_motivo():
                        before_i = stock_np(cur, bodega, producto)
                        before_l = stock_lote(cur, bodega, producto, LOT_VIG)
                        cur.execute("SAVEPOINT sp_motivo_vacio")
                        try:
                            call_baja_per(
                                cur,
                                bodega,
                                producto,
                                LOT_VIG,
                                1,
                                id_admin,
                                "   ",
                            )
                            raise RuntimeError("motivo vacio debia fallar")
                        except psycopg2.Error:
                            cur.execute("ROLLBACK TO SAVEPOINT sp_motivo_vacio")
                        except RuntimeError:
                            raise
                        if stock_np(cur, bodega, producto) != before_i:
                            raise RuntimeError("rollback inventario tras motivo invalido")
                        if stock_lote(cur, bodega, producto, LOT_VIG) != before_l:
                            raise RuntimeError("rollback lote tras motivo invalido")

                    cases.append(Case("12_rollback_motivo_vacio", t_rollback_motivo, True))

                    def t_lote_x():
                        call_baja_per(
                            cur,
                            bodega,
                            producto,
                            99999,
                            1,
                            id_admin,
                            mot_ok,
                        )

                    cases.append(Case("05_lote_inexistente", t_lote_x, False))

                    run_cases()
                except Exception as exc:
                    log(f"FAIL suite perecedero: {exc}")
                    failed += 1
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_per_suite")
                    log("Restaurado savepoint lotes 99871/99872 (sin cambios persistentes)")

    finally:
        conn.rollback()
        conn.close()

    OUT.write_text("\n".join(lines) + f"\nfailed={failed}\n", encoding="utf-8")
    print(f"written {OUT} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
