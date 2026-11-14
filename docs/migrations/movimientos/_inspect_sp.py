"""Inspección read-only de procedimientos existentes (no imprime credenciales)."""
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
OUT = Path(__file__).resolve().parent / "_sp_inspect.txt"


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
    names = [
        "sp_trasladar_perecedero",
        "sp_trasladar_no_perecedero",
        "sp_recibir_no_perecedero",
        "fn_validar_traslado",
        "fn_lotes_disponibles",
    ]
    lines: list[str] = []
    with conn.cursor() as cur:
        cur.execute("SELECT current_schema()")
        lines.append(f"schema={cur.fetchone()[0]}")
        for name in names:
            cur.execute(
                """
                SELECT p.proname, pg_get_function_identity_arguments(p.oid), pg_get_functiondef(p.oid)
                FROM pg_proc p
                JOIN pg_namespace ns ON ns.oid = p.pronamespace
                WHERE ns.nspname = 'inventario' AND p.proname = %s
                """,
                (name,),
            )
            rows = cur.fetchall()
            lines.append(f"=== {name} count={len(rows)} ===")
            for proname, args, definition in rows:
                lines.append(f"args: {args}")
                lines.append(definition)
                lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
