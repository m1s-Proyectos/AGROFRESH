"""Ejecutor puntual Etapa 10.6 — solo lectura. No imprime credenciales."""
from __future__ import annotations

import sys
from pathlib import Path

try:
    import psycopg2
    from psycopg2 import sql
except ImportError:
    sys.stderr.write("psycopg2 no instalado; ejecute: pip install psycopg2-binary\n")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[3]
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_audit_output.txt"


def load_props(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def jdbc_to_conninfo(jdbc_url: str, user: str, password: str) -> str:
    if jdbc_url.startswith("jdbc:"):
        jdbc_url = jdbc_url[5:]
    # postgresql://host:port/db?sslmode=require
    rest = jdbc_url.replace("postgresql://", "", 1)
    if "?" in rest:
        host_db, query = rest.split("?", 1)
    else:
        host_db, query = rest, "sslmode=require"
    return f"host={host_db.split('/')[0].split(':')[0]} port={host_db.split(':')[-1].split('/')[0] if ':' in host_db else 5432} dbname={host_db.split('/')[-1]} user={user} password={password} {query.replace('&', ' ')}"


def run_query(cur, title: str, stmt: str, lines: list[str]) -> None:
    lines.append(f"=== {title} ===")
    try:
        cur.execute(stmt)
        if cur.description:
            cols = [d[0] for d in cur.description]
            lines.append("\t".join(cols))
            for row in cur.fetchall():
                lines.append("\t".join("" if v is None else str(v) for v in row))
        else:
            lines.append("(ok, sin conjunto de resultados)")
    except Exception as exc:
        lines.append(f"ERROR: {type(exc).__name__}: {exc}")
    lines.append("")


def main() -> int:
    props = load_props(PROPS)
    url = props.get("spring.datasource.url")
    user = props.get("spring.datasource.username")
    password = props.get("spring.datasource.password")
    if not all([url, user, password]):
        sys.stderr.write("Faltan propiedades spring.datasource.*\n")
        return 1

    conn = psycopg2.connect(jdbc_to_conninfo(url, user, password))
    conn.set_session(readonly=True, autocommit=True)
    lines: list[str] = []

    with conn.cursor() as cur:
        run_query(cur, "Sesion", "SELECT current_database() AS db, current_user AS db_user, current_schema() AS cs", lines)
        run_query(cur, "search_path", "SHOW search_path", lines)

        run_query(
            cur,
            "Tablas modulo",
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'inventario' AND table_name = ANY (
              ARRAY['producto','perecedero','no_perecedero','categoria',
                    'lote','movimiento','inventario','detalle_compra','catalogo'])
            ORDER BY table_name
            """,
            lines,
        )

        run_query(
            cur,
            "Columnas producto/perecedero/no_perecedero/categoria",
            """
            SELECT table_name, column_name, data_type, udt_name, is_nullable,
                   character_maximum_length, numeric_precision, numeric_scale, is_identity
            FROM information_schema.columns
            WHERE table_schema = 'inventario'
              AND table_name IN ('producto','perecedero','no_perecedero','categoria')
            ORDER BY table_name, ordinal_position
            """,
            lines,
        )

        run_query(
            cur,
            "Restricciones",
            """
            SELECT c.relname AS table_name, con.conname AS constraint_name,
                   CASE con.contype WHEN 'p' THEN 'PK' WHEN 'f' THEN 'FK'
                   WHEN 'u' THEN 'UNIQUE' WHEN 'c' THEN 'CHECK' ELSE con.contype::text END AS tipo,
                   pg_get_constraintdef(con.oid, true) AS definicion
            FROM pg_constraint con
            JOIN pg_class c ON c.oid = con.conrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'inventario'
              AND c.relname IN ('producto','perecedero','no_perecedero','categoria',
                                'lote','movimiento','inventario','detalle_compra','catalogo')
            ORDER BY c.relname, con.conname
            """,
            lines,
        )

        run_query(
            cur,
            "Indices",
            """
            SELECT tablename, indexname FROM pg_indexes
            WHERE schemaname = 'inventario'
              AND tablename IN ('producto','perecedero','no_perecedero','categoria',
                                'lote','movimiento','inventario','detalle_compra','catalogo')
            ORDER BY tablename, indexname
            """,
            lines,
        )

        run_query(
            cur,
            "Secuencia id_producto",
            "SELECT pg_get_serial_sequence('inventario.producto', 'id_producto') AS seq",
            lines,
        )

        run_query(
            cur,
            "EXISTS sin prefijo (search_path sesion)",
            """
            SELECT
              (SELECT EXISTS(SELECT 1 FROM lote WHERE id_producto = -1)) AS lote,
              (SELECT EXISTS(SELECT 1 FROM movimiento WHERE id_producto = -1)) AS movimiento,
              (SELECT EXISTS(SELECT 1 FROM inventario WHERE id_producto = -1)) AS inventario,
              (SELECT EXISTS(SELECT 1 FROM detalle_compra WHERE id_producto = -1)) AS detalle_compra,
              (SELECT EXISTS(SELECT 1 FROM catalogo WHERE id_producto = -1)) AS catalogo
            """,
            lines,
        )

        run_query(
            cur,
            "EXISTS con prefijo inventario.",
            """
            SELECT
              (SELECT EXISTS(SELECT 1 FROM inventario.lote WHERE id_producto = -1)) AS lote,
              (SELECT EXISTS(SELECT 1 FROM inventario.inventario WHERE id_producto = -1)) AS inventario
            """,
            lines,
        )

        # search_path solo public (simula riesgo JDBC sin inventario)
        run_query(cur, "search_path public", "SET search_path TO public", lines)
        run_query(
            cur,
            "EXISTS sin prefijo tras SET public",
            "SELECT (SELECT EXISTS(SELECT 1 FROM lote WHERE id_producto = -1)) AS lote_public",
            lines,
        )
        run_query(cur, "restaurar search_path", "SET search_path TO inventario, public", lines)

        for title, stmt in [
            ("cnt_producto", "SELECT COUNT(*) AS n FROM inventario.producto"),
            ("cnt_perecedero", "SELECT COUNT(*) AS n FROM inventario.perecedero"),
            ("cnt_no_perecedero", "SELECT COUNT(*) AS n FROM inventario.no_perecedero"),
            (
                "productos_sin_clasificacion",
                """
                SELECT COUNT(*) AS n FROM inventario.producto p
                WHERE NOT EXISTS (SELECT 1 FROM inventario.perecedero pe WHERE pe.id_producto = p.id_producto)
                  AND NOT EXISTS (SELECT 1 FROM inventario.no_perecedero np WHERE np.id_producto = p.id_producto)
                """,
            ),
            (
                "productos_con_ambas_clasificaciones",
                """
                SELECT COUNT(*) AS n FROM inventario.producto p
                WHERE EXISTS (SELECT 1 FROM inventario.perecedero pe WHERE pe.id_producto = p.id_producto)
                  AND EXISTS (SELECT 1 FROM inventario.no_perecedero np WHERE np.id_producto = p.id_producto)
                """,
            ),
            (
                "perecedero_huerfano",
                """
                SELECT COUNT(*) AS n FROM inventario.perecedero pe
                WHERE NOT EXISTS (SELECT 1 FROM inventario.producto p WHERE p.id_producto = pe.id_producto)
                """,
            ),
            (
                "no_perecedero_huerfano",
                """
                SELECT COUNT(*) AS n FROM inventario.no_perecedero np
                WHERE NOT EXISTS (SELECT 1 FROM inventario.producto p WHERE p.id_producto = np.id_producto)
                """,
            ),
            (
                "producto_categoria_inexistente",
                """
                SELECT COUNT(*) AS n FROM inventario.producto p
                WHERE NOT EXISTS (SELECT 1 FROM inventario.categoria c WHERE c.id_categoria = p.id_categoria)
                """,
            ),
            (
                "productos_con_lote",
                "SELECT COUNT(DISTINCT id_producto) AS n FROM inventario.lote WHERE id_producto IS NOT NULL",
            ),
            (
                "productos_con_movimiento",
                "SELECT COUNT(DISTINCT id_producto) AS n FROM inventario.movimiento WHERE id_producto IS NOT NULL",
            ),
            (
                "productos_en_inventario",
                "SELECT COUNT(DISTINCT id_producto) AS n FROM inventario.inventario WHERE id_producto IS NOT NULL",
            ),
            (
                "productos_en_detalle_compra",
                "SELECT COUNT(DISTINCT id_producto) AS n FROM inventario.detalle_compra WHERE id_producto IS NOT NULL",
            ),
            (
                "productos_en_catalogo",
                "SELECT COUNT(DISTINCT id_producto) AS n FROM inventario.catalogo WHERE id_producto IS NOT NULL",
            ),
        ]:
            run_query(cur, title, stmt, lines)

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Auditoria escrita en: {OUT.relative_to(ROOT)}")
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
