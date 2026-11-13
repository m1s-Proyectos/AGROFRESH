"""Pruebas sp_baja_vencimiento — savepoints; sin persistencia de datos de prueba."""
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
OUT = Path(__file__).resolve().parent / "_test_12_5b_2_output.txt"

LOT_VIG = 99881
LOT_VEN = 99882
LOT_HOY = 99883


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


def call_baja_venc(cur, bodega, producto, num_lote, cantidad, usuario, motivo) -> int | None:
    cur.execute(
        "CALL inventario.sp_baja_vencimiento(%s,%s,%s,%s,%s,%s,NULL)",
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


def seed_lotes(cur, id_bodega: int, id_producto: int, id_proveedor: int) -> None:
    cur.execute(
        """
        INSERT INTO inventario.inventario (id_bodega, id_producto, cantidad_actual, stock_minimo)
        VALUES (%s, %s, 60, 0)
        ON CONFLICT (id_bodega, id_producto)
        DO UPDATE SET cantidad_actual = 60
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
            cantidad_actual = 20
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
            cantidad_actual = 15
        """,
        (LOT_VEN, id_bodega, id_producto, id_proveedor),
    )
    cur.execute(
        """
        INSERT INTO inventario.lote (
            num_lote, id_bodega, id_producto, id_proveedor,
            fech_vencimiento, cantidad_recibida, cantidad_actual
        ) VALUES (%s,%s,%s,%s,CURRENT_DATE,10,10)
        ON CONFLICT (num_lote, id_bodega, id_producto)
        DO UPDATE SET
            fech_vencimiento = EXCLUDED.fech_vencimiento,
            cantidad_actual = 10
        """,
        (LOT_HOY, id_bodega, id_producto, id_proveedor),
    )


def stock_inv(cur, bodega: int, producto: int) -> int:
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

    mot_ok = "Prueba 12.5B.2 vencimiento"

    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            id_admin = fetch_user(cur, "ADMINISTRADOR")
            id_oper = fetch_user(cur, "OPERATIVO")
            log(f"fixtures admin={id_admin} operativo={id_oper}")
            if not id_admin or not id_oper:
                log("FAIL: usuarios de rol")
                failed += 1
                OUT.write_text("\n".join(lines) + f"\nfailed={failed}\n", encoding="utf-8")
                conn.rollback()
                return 1

            cur.execute(
                """
                SELECT p.id_producto
                FROM inventario.perecedero pe
                JOIN inventario.producto p ON p.id_producto = pe.id_producto
                LIMIT 1
                """
            )
            row_prod = cur.fetchone()
            cur.execute(
                "SELECT id_bodega FROM inventario.bodega ORDER BY id_bodega LIMIT 1"
            )
            id_bodega = cur.fetchone()[0]
            cur.execute(
                "SELECT id_proveedor FROM inventario.proveedor ORDER BY id_proveedor LIMIT 1"
            )
            id_prov = cur.fetchone()[0]

            if not row_prod:
                log("FAIL: sin perecedero")
                failed += 1
            else:
                id_producto = row_prod[0]
                cases: list[Case] = []

                def run_cases() -> None:
                    nonlocal failed
                    for case in cases:
                        cur.execute("SAVEPOINT sp_case")
                        try:
                            if case.expect_ok:
                                case.fn()
                                log(f"OK {case.name}")
                            elif expect_exception(case.fn):
                                log(f"OK {case.name} (rechazado)")
                            else:
                                log(f"FAIL {case.name} (esperaba error)")
                                failed += 1
                        except Exception as exc:
                            log(f"FAIL {case.name}: {type(exc).__name__}: {exc}")
                            failed += 1
                        finally:
                            cur.execute("ROLLBACK TO SAVEPOINT sp_case")

                cur.execute("SAVEPOINT sp_suite")
                try:
                    seed_lotes(cur, id_bodega, id_producto, id_prov)
                    bodega, producto = id_bodega, id_producto

                    def t_venc_ok():
                        bi, bl = stock_inv(cur, bodega, producto), stock_lote(
                            cur, bodega, producto, LOT_VEN
                        )
                        mov = call_baja_venc(
                            cur, bodega, producto, LOT_VEN, 2, id_admin, mot_ok
                        )
                        if stock_lote(cur, bodega, producto, LOT_VEN) != bl - 2:
                            raise RuntimeError("lote no descontado")
                        if stock_inv(cur, bodega, producto) != bi - 2:
                            raise RuntimeError("inventario incoherente")
                        cur.execute(
                            """
                            SELECT tipo_movimiento, cantidad, num_lote
                            FROM inventario.movimiento WHERE id_movimiento=%s
                            """,
                            (mov,),
                        )
                        if cur.fetchone() != ("BAJA_VENCIMIENTO", 2, LOT_VEN):
                            raise RuntimeError("movimiento incorrecto")

                    cases.append(Case("01_baja_valida_vencido", t_venc_ok, True))

                    def t_vigente_rechazo():
                        call_baja_venc(
                            cur, bodega, producto, LOT_VIG, 1, id_admin, mot_ok
                        )

                    cases.append(Case("02_rechazo_lote_vigente", t_vigente_rechazo, False))

                    def t_hoy_ok():
                        mov = call_baja_venc(
                            cur, bodega, producto, LOT_HOY, 1, id_admin, mot_ok
                        )
                        cur.execute(
                            "SELECT tipo_movimiento FROM inventario.movimiento WHERE id_movimiento=%s",
                            (mov,),
                        )
                        if cur.fetchone()[0] != "BAJA_VENCIMIENTO":
                            raise RuntimeError("tipo movimiento")

                    cases.append(Case("03_vencido_exactamente_hoy", t_hoy_ok, True))

                    def t_cero():
                        call_baja_venc(
                            cur, bodega, producto, LOT_VEN, 0, id_admin, mot_ok
                        )

                    cases.append(Case("04_cantidad_cero", t_cero, False))

                    def t_neg():
                        call_baja_venc(
                            cur, bodega, producto, LOT_VEN, -1, id_admin, mot_ok
                        )

                    cases.append(Case("05_cantidad_negativa", t_neg, False))

                    def t_insuf():
                        call_baja_venc(
                            cur,
                            bodega,
                            producto,
                            LOT_VEN,
                            99_999,
                            id_admin,
                            mot_ok,
                        )

                    cases.append(Case("06_existencias_insuficientes", t_insuf, False))

                    def t_lote_x():
                        call_baja_venc(
                            cur, bodega, producto, 99998, 1, id_admin, mot_ok
                        )

                    cases.append(Case("07_lote_inexistente", t_lote_x, False))

                    def t_prod_x():
                        call_baja_venc(
                            cur, bodega, -1, LOT_VEN, 1, id_admin, mot_ok
                        )

                    cases.append(Case("08_producto_inexistente", t_prod_x, False))

                    def t_bod_x():
                        call_baja_venc(
                            cur, -1, producto, LOT_VEN, 1, id_admin, mot_ok
                        )

                    cases.append(Case("09_bodega_inexistente", t_bod_x, False))

                    def t_oper():
                        call_baja_venc(
                            cur, bodega, producto, LOT_VEN, 1, id_oper, mot_ok
                        )

                    cases.append(Case("10_operativo_rechazado", t_oper, False))

                    def t_admin():
                        call_baja_venc(
                            cur, bodega, producto, LOT_VEN, 1, id_admin, mot_ok
                        )

                    cases.append(Case("11_admin_aceptado", t_admin, True))

                    def t_bitacora():
                        mov = call_baja_venc(
                            cur,
                            bodega,
                            producto,
                            LOT_VEN,
                            1,
                            id_admin,
                            "Motivo bitacora vencimiento",
                        )
                        cur.execute(
                            """
                            SELECT tipo_accion, tabla_afectada, descripcion
                            FROM inventario.bitacora
                            WHERE tipo_accion='BAJA_VENCIMIENTO'
                              AND descripcion LIKE %s
                            ORDER BY id_bitacora DESC LIMIT 1
                            """,
                            (f"%movimiento #{mov}%",),
                        )
                        row = cur.fetchone()
                        if not row or "Motivo bitacora vencimiento" not in row[2]:
                            raise RuntimeError(f"bitacora: {row}")

                    cases.append(Case("12_movimiento_y_13_bitacora", t_bitacora, True))

                    def t_rollback():
                        bi = stock_inv(cur, bodega, producto)
                        bl = stock_lote(cur, bodega, producto, LOT_VEN)
                        cur.execute("SAVEPOINT sp_err")
                        try:
                            call_baja_venc(
                                cur,
                                bodega,
                                producto,
                                LOT_VEN,
                                1,
                                id_admin,
                                "  ",
                            )
                            raise RuntimeError("debía fallar motivo")
                        except psycopg2.Error:
                            cur.execute("ROLLBACK TO SAVEPOINT sp_err")
                        if stock_inv(cur, bodega, producto) != bi:
                            raise RuntimeError("inventario tras error")
                        if stock_lote(cur, bodega, producto, LOT_VEN) != bl:
                            raise RuntimeError("lote tras error")

                    cases.append(Case("14_rollback_registro", t_rollback, True))

                    def t_consistencia():
                        inv_antes = stock_inv(cur, bodega, producto)
                        lot_antes = stock_lote(cur, bodega, producto, LOT_VEN)
                        qty = 1
                        call_baja_venc(
                            cur, bodega, producto, LOT_VEN, qty, id_admin, mot_ok
                        )
                        if stock_inv(cur, bodega, producto) != inv_antes - qty:
                            raise RuntimeError("inventario no alineado")
                        if stock_lote(cur, bodega, producto, LOT_VEN) != lot_antes - qty:
                            raise RuntimeError("lote no alineado con inventario")

                    cases.append(Case("15_consistencia_inventario_lote", t_consistencia, True))

                    run_cases()
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_suite")
                    log(
                        "Restaurado savepoint lotes 99881-99883 (sin cambios persistentes)"
                    )

    finally:
        conn.rollback()
        conn.close()

    OUT.write_text("\n".join(lines) + f"\nfailed={failed}\n", encoding="utf-8")
    print(f"written {OUT} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
