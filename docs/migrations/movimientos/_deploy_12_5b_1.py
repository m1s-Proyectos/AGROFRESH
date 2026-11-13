"""Despliega MIG-12.5B.1 en Supabase (no imprime credenciales)."""
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
SQL = Path(__file__).resolve().parent / "MIG-12.5B.1_sp_baja_dano.sql"


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
    conn.autocommit = True
    sql_text = SQL.read_text(encoding="utf-8")
    with conn.cursor() as cur:
        cur.execute(sql_text)
    print(f"deploy_ok: {SQL.name}")
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
