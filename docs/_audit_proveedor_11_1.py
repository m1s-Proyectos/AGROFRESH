"""Etapa 11.1 — auditoría solo lectura proveedor. No imprime credenciales."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parent.parent
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_proveedor_audit_local.txt"


def load_props() -> dict[str, str]:
    data: dict[str, str] = {}
    for line in PROPS.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, v = line.split("=", 1)
            data[k.strip()] = v.strip()
    return data


def connect(props: dict[str, str]):
    url = props["spring.datasource.url"].replace("jdbc:", "")
    hostpart = url.split("://")[1]
    host, port = hostpart.split("/")[0].split(":")
    db = hostpart.split("/")[1].split("?")[0]
    return psycopg2.connect(
        host=host,
        port=port,
        dbname=db,
        user=props["spring.datasource.username"],
        password=props["spring.datasource.password"],
        sslmode="require",
    )


def run(cur, title: str, sql: str, lines: list[str]) -> None:
    lines.append(f"=== {title} ===")
    cur.execute(sql)
    if cur.description:
        cols = [d[0] for d in cur.description]
        lines.append("\t".join(cols))
        for row in cur.fetchall():
            lines.append("\t".join("" if v is None else str(v) for v in row))
    else:
        lines.append("(ok)")
    lines.append("")


def main() -> None:
    props = load_props()
    conn = connect(props)
    conn.set_session(readonly=True, autocommit=True)
    lines: list[str] = []
    with conn.cursor() as cur:
        run(
            cur,
            "columnas proveedor",
            """
            SELECT column_name, data_type, udt_name, is_nullable, character_maximum_length,
                   numeric_precision, numeric_scale, is_identity, column_default
            FROM information_schema.columns
            WHERE table_schema='inventario' AND table_name='proveedor'
            ORDER BY ordinal_position
            """,
            lines,
        )
        run(
            cur,
            "restricciones proveedor",
            """
            SELECT con.conname, CASE con.contype WHEN 'p' THEN 'PK' WHEN 'f' THEN 'FK'
                   WHEN 'u' THEN 'UNIQUE' WHEN 'c' THEN 'CHECK' ELSE con.contype::text END AS tipo,
                   pg_get_constraintdef(con.oid, true) AS def
            FROM pg_constraint con
            JOIN pg_class c ON c.oid=con.conrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='inventario' AND c.relname='proveedor'
            ORDER BY con.conname
            """,
            lines,
        )
        run(
            cur,
            "indices proveedor",
            """
            SELECT indexname, indexdef FROM pg_indexes
            WHERE schemaname='inventario' AND tablename='proveedor' ORDER BY indexname
            """,
            lines,
        )
        run(
            cur,
            "fk_entrantes",
            """
            SELECT con.conname, c.relname AS tabla_origen, pg_get_constraintdef(con.oid,true) AS def
            FROM pg_constraint con
            JOIN pg_class c ON c.oid=con.conrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE con.contype='f' AND con.confrelid = (
              SELECT oid FROM pg_class WHERE relname='proveedor'
                AND relnamespace=(SELECT oid FROM pg_namespace WHERE nspname='inventario'))
            ORDER BY c.relname
            """,
            lines,
        )
        run(
            cur,
            "permisos proveedor",
            """
            SELECT p.recurso, p.accion, r.nombre AS rol
            FROM permiso p
            JOIN rol_permiso rp ON rp.id_permiso = p.id_permiso
            JOIN rol r ON r.id_rol = rp.id_rol
            WHERE p.recurso = 'proveedor'
            ORDER BY p.accion, r.nombre
            """,
            lines,
        )
        run(
            cur,
            "permisos proveedor catalogo",
            """
            SELECT DISTINCT p.recurso, p.accion FROM permiso p
            WHERE p.recurso='proveedor' ORDER BY p.accion
            """,
            lines,
        )
        aggregates = [
            ("cnt_proveedor", "SELECT COUNT(*) AS n FROM inventario.proveedor"),
            ("cnt_compra", "SELECT COUNT(*) AS n FROM inventario.compra"),
            (
                "proveedores_con_compra",
                "SELECT COUNT(DISTINCT id_proveedor) AS n FROM inventario.compra",
            ),
            (
                "compras_sin_proveedor_valido",
                """
                SELECT COUNT(*) AS n FROM inventario.compra c
                WHERE NOT EXISTS (
                  SELECT 1 FROM inventario.proveedor p WHERE p.id_proveedor=c.id_proveedor)
                """,
            ),
            (
                "proveedores_en_catalogo",
                "SELECT COUNT(DISTINCT id_proveedor) AS n FROM inventario.catalogo",
            ),
            (
                "proveedores_en_lote",
                "SELECT COUNT(DISTINCT id_proveedor) AS n FROM inventario.lote",
            ),
            (
                "proveedores_solo_dui",
                """
                SELECT COUNT(*) AS n FROM inventario.proveedor
                WHERE dui IS NOT NULL AND (nit IS NULL OR btrim(nit) = '')
                """,
            ),
            (
                "proveedores_solo_nit",
                """
                SELECT COUNT(*) AS n FROM inventario.proveedor
                WHERE nit IS NOT NULL AND btrim(nit) <> '' AND dui IS NULL
                """,
            ),
            (
                "proveedores_ambos_id",
                """
                SELECT COUNT(*) AS n FROM inventario.proveedor
                WHERE dui IS NOT NULL AND nit IS NOT NULL AND btrim(nit) <> ''
                """,
            ),
            (
                "proveedores_sin_dui_ni_nit",
                """
                SELECT COUNT(*) AS n FROM inventario.proveedor
                WHERE dui IS NULL AND (nit IS NULL OR btrim(nit) = '')
                """,
            ),
            (
                "correo_nulo",
                "SELECT COUNT(*) AS n FROM inventario.proveedor WHERE correo_electronico IS NULL",
            ),
            (
                "valores_distintos_iva",
                """
                SELECT COUNT(DISTINCT iva) AS distintos,
                       COUNT(*) FILTER (WHERE iva IS NULL) AS nulos
                FROM inventario.proveedor
                """,
            ),
            (
                "secuencia",
                "SELECT pg_get_serial_sequence('inventario.proveedor','id_proveedor') AS seq",
            ),
        ]
        for title, sql in aggregates:
            run(cur, title, sql, lines)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"OK -> {OUT.relative_to(ROOT)}")
    conn.close()


if __name__ == "__main__":
    main()
