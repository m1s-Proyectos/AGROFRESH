"""Auditoría 12.5B.0 — solo lectura (no imprime credenciales)."""
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
OUT = Path(__file__).resolve().parent / "_audit_12_5b_output.txt"


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


def run(cur, title: str, sql: str, lines: list[str]) -> None:
    lines.append(f"=== {title} ===")
    try:
        cur.execute(sql)
        if not cur.description:
            lines.append("(ok)")
            lines.append("")
            return
        cols = [d[0] for d in cur.description]
        lines.append("\t".join(cols))
        for row in cur.fetchall():
            lines.append("\t".join("" if v is None else str(v) for v in row))
    except Exception as exc:
        lines.append(f"ERROR {type(exc).__name__}: {exc}")
    lines.append("")


def main() -> int:
    props = load_props(PROPS)
    conn = psycopg2.connect(
        jdbc_conninfo(
            props["spring.datasource.url"],
            props["spring.datasource.username"],
            props["spring.datasource.password"],
        )
    )
    conn.set_session(readonly=True, autocommit=True)
    lines: list[str] = []
    with conn.cursor() as cur:
        run(
            cur,
            "sesion",
            "SELECT current_database(), current_user, current_schema(), current_setting('search_path')",
            lines,
        )
        tables = [
            "inventario",
            "lote",
            "movimiento",
            "movimiento_bodega",
            "movimiento_compra",
            "bitacora",
            "producto",
            "perecedero",
            "no_perecedero",
            "bodega",
            "usuario",
            "rol",
            "permiso",
            "rol_permiso",
        ]
        lines.append("=== columnas ===")
        cur.execute(
            """
            SELECT table_name, column_name, data_type, udt_name, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'inventario'
              AND table_name = ANY(%s)
            ORDER BY table_name, ordinal_position
            """,
            (tables,),
        )
        cols = [d[0] for d in cur.description]
        lines.append("\t".join(cols))
        for row in cur.fetchall():
            lines.append("\t".join("" if v is None else str(v) for v in row))
        lines.append("")

        run(
            cur,
            "pk_fk",
            """
            SELECT tc.table_name, tc.constraint_name, tc.constraint_type, kcu.column_name,
                   ccu.table_name AS foreign_table, ccu.column_name AS foreign_column
            FROM information_schema.table_constraints tc
            LEFT JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
            LEFT JOIN information_schema.constraint_column_usage ccu
              ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
            WHERE tc.table_schema = 'inventario'
              AND tc.table_name = ANY(ARRAY['movimiento','lote','inventario','bitacora'])
            ORDER BY tc.table_name, tc.constraint_type, tc.constraint_name
            """,
            lines,
        )
        run(
            cur,
            "check_movimiento",
            """
            SELECT con.conname, pg_get_constraintdef(con.oid)
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
            WHERE nsp.nspname = 'inventario' AND rel.relname = 'movimiento'
            """,
            lines,
        )
        run(
            cur,
            "check_bitacora",
            """
            SELECT con.conname, pg_get_constraintdef(con.oid)
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
            WHERE nsp.nspname = 'inventario' AND rel.relname = 'bitacora'
            """,
            lines,
        )
        run(
            cur,
            "rutinas_movimiento",
            """
            SELECT p.proname, pg_get_function_identity_arguments(p.oid) AS args,
                   CASE p.prosecdef WHEN true THEN 'DEFINER' ELSE 'INVOKER' END AS security
            FROM pg_proc p
            JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'inventario'
              AND (p.proname LIKE 'sp_%' OR p.proname LIKE 'fn_%')
            ORDER BY p.proname
            """,
            lines,
        )
        for name in (
            "sp_salida_no_perecedero",
            "sp_salida_perecedero",
            "fn_validar_salida",
            "fn_stock_lotes_vigentes",
        ):
            cur.execute(
                """
                SELECT pg_get_functiondef(p.oid)
                FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE n.nspname='inventario' AND p.proname=%s
                LIMIT 1
                """,
                (name,),
            )
            row = cur.fetchone()
            lines.append(f"=== def_{name} ===")
            lines.append(row[0] if row else "NO ENCONTRADO")
            lines.append("")

        run(
            cur,
            "permisos_movimiento_rol",
            """
            SELECT r.nombre AS rol, p.accion, p.recurso
            FROM inventario.rol_permiso rp
            JOIN inventario.rol r ON r.id_rol = rp.id_rol
            JOIN inventario.permiso p ON p.id_permiso = rp.id_permiso
            WHERE p.recurso IN ('movimiento','inventario','lote','bitacora')
            ORDER BY r.nombre, p.recurso, p.accion
            """,
            lines,
        )
        run(
            cur,
            "bitacora_conteo",
            "SELECT COUNT(*) AS filas FROM inventario.bitacora",
            lines,
        )
        run(
            cur,
            "movimiento_tipos_en_datos",
            """
            SELECT tipo_movimiento, COUNT(*) AS n
            FROM inventario.movimiento
            GROUP BY tipo_movimiento
            ORDER BY tipo_movimiento
            """,
            lines,
        )
        run(
            cur,
            "rls",
            """
            SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual
            FROM pg_policies
            WHERE schemaname = 'inventario'
            """,
            lines,
        )
        run(
            cur,
            "grants_salida",
            """
            SELECT grantee, privilege_type
            FROM information_schema.routine_privileges
            WHERE routine_schema = 'inventario'
              AND routine_name IN ('sp_salida_no_perecedero','sp_salida_perecedero')
            ORDER BY grantee, privilege_type
            """,
            lines,
        )
        run(
            cur,
            "usuario_rol_muestra",
            """
            SELECT u.id_usuario, r.nombre AS rol, u.activo, u.bloqueado
            FROM inventario.usuario u
            JOIN inventario.rol r ON r.id_rol = u.id_rol
            ORDER BY u.id_usuario
            LIMIT 5
            """,
            lines,
        )
        run(
            cur,
            "bitacora_muestra_redactada",
            """
            SELECT id_bitacora, id_usuario, fecha_hora, tipo_accion, tabla_afectada,
                   LEFT(descripcion, 80) AS descripcion
            FROM inventario.bitacora
            ORDER BY id_bitacora DESC
            LIMIT 5
            """,
            lines,
        )

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
