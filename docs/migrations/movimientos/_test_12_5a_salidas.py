"""Pruebas de sp_salida_* en transacción con ROLLBACK (no deja datos)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import psycopg2
except ImportError:
    sys.stderr.write("pip install psycopg2-binary\n")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[3]
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_test_12_5a_output.txt"


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

    def get_fixtures(cur) -> dict:
        cur.execute(
            """
            SELECT u.id_usuario
            FROM usuario u
            JOIN rol r ON r.id_rol = u.id_rol
            WHERE u.activo = TRUE AND NOT u.bloqueado AND r.activo = TRUE
            LIMIT 1
            """
        )
        row = cur.fetchone()
        if not row:
            raise RuntimeError("No hay usuario activo para pruebas")
        id_usuario = row[0]

        cur.execute(
            """
            SELECT p.id_producto, i.id_bodega, i.cantidad_actual
            FROM no_perecedero np
            JOIN producto p ON p.id_producto = np.id_producto
            JOIN inventario i ON i.id_producto = p.id_producto
            WHERE i.cantidad_actual > 0
            ORDER BY i.cantidad_actual DESC
            LIMIT 1
            """
        )
        np_row = cur.fetchone()

        cur.execute(
            """
            SELECT p.id_producto, l.id_bodega
            FROM perecedero pe
            JOIN producto p ON p.id_producto = pe.id_producto
            JOIN lote l ON l.id_producto = p.id_producto
            WHERE l.cantidad_actual > 0 AND l.fech_vencimiento > CURRENT_DATE
            GROUP BY p.id_producto, l.id_bodega
            HAVING COUNT(*) >= 1
            LIMIT 1
            """
        )
        p_row = cur.fetchone()
        return {
            "id_usuario": id_usuario,
            "np": np_row,
            "per": p_row,
        }

    def call_salida_np(cur, bodega, producto, cantidad, usuario) -> int | None:
        cur.execute(
            "CALL inventario.sp_salida_no_perecedero(%s, %s, %s, %s, NULL)",
            (bodega, producto, cantidad, usuario),
        )
        if cur.description:
            row = cur.fetchone()
            return row[0] if row else None
        return None

    def call_salida_per(cur, bodega, producto, cantidad, usuario) -> int | None:
        cur.execute(
            "CALL inventario.sp_salida_perecedero(%s, %s, %s, %s, NULL)",
            (bodega, producto, cantidad, usuario),
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

    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            fx = get_fixtures(cur)
            log(f"fixtures usuario={fx['id_usuario']} np={fx['np']} per={fx['per']}")

            cases: list[Case] = []

            if fx["np"]:
                bodega, producto, stock = fx["np"][1], fx["np"][0], fx["np"][2]
                cant_ok = min(1, stock)

                def t_np_ok():
                    call_salida_np(cur, bodega, producto, cant_ok, fx["id_usuario"])

                cases.append(Case("no_perecedero_salida_ok", t_np_ok, True))

                def t_np_insuf():
                    call_salida_np(cur, bodega, producto, stock + 9999, fx["id_usuario"])

                cases.append(
                    Case("no_perecedero_stock_insuficiente", t_np_insuf, False)
                )

            def t_cero():
                call_salida_np(cur, 1, 1, 0, fx["id_usuario"])

            cases.append(Case("cantidad_cero", t_cero, False))

            def t_neg():
                call_salida_np(cur, 1, 1, -5, fx["id_usuario"])

            cases.append(Case("cantidad_negativa", t_neg, False))

            def t_prod_x():
                call_salida_np(cur, 1, -1, 1, fx["id_usuario"])

            cases.append(Case("producto_invalido", t_prod_x, False))

            def t_bod_x():
                call_salida_np(cur, -1, 1, 1, fx["id_usuario"])

            cases.append(Case("bodega_invalida", t_bod_x, False))

            if fx["per"]:
                bodega_p, producto_p = fx["per"][1], fx["per"][0]
                cur.execute(
                    """
                    SELECT COALESCE(SUM(cantidad_actual),0)
                    FROM lote
                    WHERE id_bodega=%s AND id_producto=%s
                      AND cantidad_actual > 0 AND fech_vencimiento > CURRENT_DATE
                    """,
                    (bodega_p, producto_p),
                )
                vigente = cur.fetchone()[0]
                if vigente and vigente >= 2:
                    cant_fefo = min(2, vigente)

                    def t_per_ok():
                        call_salida_per(
                            cur, bodega_p, producto_p, cant_fefo, fx["id_usuario"]
                        )

                    cases.append(Case("perecedero_salida_fefo", t_per_ok, True))

                cur.execute(
                    """
                    SELECT num_lote FROM lote
                    WHERE id_bodega=%s AND id_producto=%s
                      AND fech_vencimiento <= CURRENT_DATE AND cantidad_actual > 0
                    LIMIT 1
                    """,
                    (bodega_p, producto_p),
                )
                venc = cur.fetchone()
                if venc:
                    cur.execute(
                        """
                        SELECT COALESCE(SUM(cantidad_actual),0)
                        FROM lote
                        WHERE id_bodega=%s AND id_producto=%s
                          AND cantidad_actual > 0 AND fech_vencimiento > CURRENT_DATE
                        """,
                        (bodega_p, producto_p),
                    )
                    solo_vig = cur.fetchone()[0] or 0

                    def t_sin_vig():
                        call_salida_per(
                            cur,
                            bodega_p,
                            producto_p,
                            solo_vig + 1000,
                            fx["id_usuario"],
                        )

                    cases.append(
                        Case("perecedero_sin_lotes_vigentes_suficientes", t_sin_vig, False)
                    )
            else:
                log("PENDIENTE: sin producto perecedero con lotes vigentes para FEFO")

            for case in cases:
                cur.execute("SAVEPOINT sp_case")
                try:
                    if case.expect_ok:
                        case.fn()
                        log(f"OK {case.name}")
                    else:
                        ok = expect_exception(case.fn)
                        if ok:
                            log(f"OK {case.name} (rechazado)")
                        else:
                            log(f"FAIL {case.name} (esperaba error)")
                            failed += 1
                except Exception as exc:
                    log(f"FAIL {case.name}: {type(exc).__name__}: {exc}")
                    failed += 1
                finally:
                    cur.execute("ROLLBACK TO SAVEPOINT sp_case")

            # Verificación FEFO con lotes de prueba sintéticos en la misma transacción
            cur.execute("SAVEPOINT sp_fefo_demo")
            try:
                cur.execute(
                    """
                    SELECT p.id_producto
                    FROM perecedero pe
                    JOIN producto p ON p.id_producto = pe.id_producto
                    LIMIT 1
                    """
                )
                base = cur.fetchone()
                cur.execute("SELECT id_proveedor FROM proveedor ORDER BY id_proveedor LIMIT 1")
                prov_row = cur.fetchone()
                cur.execute("SELECT id_bodega FROM bodega ORDER BY id_bodega LIMIT 1")
                id_bodega = cur.fetchone()[0]
                if base and prov_row:
                    prod, prov = base[0], prov_row[0]
                    cur.execute(
                        """
                        INSERT INTO inventario (id_bodega, id_producto, cantidad_actual, stock_minimo)
                        VALUES (%s, %s, 60, 0)
                        ON CONFLICT (id_bodega, id_producto)
                        DO UPDATE SET cantidad_actual = 60
                        """,
                        (id_bodega, prod),
                    )
                    cur.execute(
                        """
                        UPDATE lote
                        SET cantidad_actual = 0
                        WHERE id_bodega = %s AND id_producto = %s
                          AND num_lote NOT IN (99901, 99902, 99903)
                        """,
                        (id_bodega, prod),
                    )
                    for num, venc, qty in (
                        (99901, "2026-12-15", 10),
                        (99902, "2027-01-20", 20),
                        (99903, "2027-03-15", 30),
                    ):
                        cur.execute(
                            """
                            INSERT INTO lote (
                                num_lote, id_bodega, id_producto, id_proveedor,
                                fech_vencimiento, cantidad_recibida, cantidad_actual
                            ) VALUES (%s,%s,%s,%s,%s::date,%s,%s)
                            ON CONFLICT (num_lote, id_bodega, id_producto)
                            DO UPDATE SET
                                fech_vencimiento = EXCLUDED.fech_vencimiento,
                                cantidad_actual = EXCLUDED.cantidad_actual,
                                cantidad_recibida = GREATEST(lote.cantidad_recibida, EXCLUDED.cantidad_recibida)
                            """,
                            (num, id_bodega, prod, prov, venc, qty, qty),
                        )
                    call_salida_per(cur, id_bodega, prod, 25, fx["id_usuario"])
                    cur.execute(
                        """
                        SELECT num_lote, cantidad_actual FROM lote
                        WHERE id_bodega=%s AND id_producto=%s AND num_lote IN (99901,99902,99903)
                        ORDER BY num_lote
                        """,
                        (id_bodega, prod),
                    )
                    lots_after = cur.fetchall()
                    cur.execute(
                        """
                        SELECT num_lote, cantidad FROM movimiento
                        WHERE tipo_movimiento='SALIDA' AND id_bodega=%s AND id_producto=%s
                          AND num_lote IN (99901,99902,99903)
                        ORDER BY num_lote
                        """,
                        (id_bodega, prod),
                    )
                    movs = cur.fetchall()
                    log(f"FEFO_DEMO lotes_after={lots_after} movs={movs}")
                    expect_l1 = next((q for n, q in lots_after if n == 99901), None)
                    expect_l2 = next((q for n, q in lots_after if n == 99902), None)
                    m1 = sum(c for n, c in movs if n == 99901)
                    m2 = sum(c for n, c in movs if n == 99902)
                    if expect_l1 == 0 and expect_l2 == 5 and m1 == 10 and m2 == 15:
                        log("OK fefo_tres_lotes_distribucion_25")
                    else:
                        log(
                            f"FAIL fefo_tres_lotes esperado L001=0,L002=5,movs 10+15; "
                            f"got {lots_after} {movs}"
                        )
                        failed += 1
                else:
                    log("PENDIENTE fefo_demo sin perecedero base")
            except Exception as exc:
                log(f"FAIL fefo_demo: {exc}")
                failed += 1
            cur.execute("ROLLBACK TO SAVEPOINT sp_fefo_demo")

    finally:
        conn.rollback()
        conn.close()

    OUT.write_text("\n".join(lines) + f"\nfailed={failed}\n", encoding="utf-8")
    print(f"written {OUT} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
