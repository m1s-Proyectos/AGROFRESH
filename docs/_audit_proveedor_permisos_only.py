"""Solo lectura: permisos recurso proveedor. No imprime credenciales."""
from pathlib import Path
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
PROPS = ROOT / "config" / "application-local.properties"
OUT = Path(__file__).resolve().parent / "_proveedor_permisos_audit.txt"


def main() -> int:
    props: dict[str, str] = {}
    for line in PROPS.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    url = props["spring.datasource.url"].replace("jdbc:", "")
    hostpart = url.split("://")[1]
    host, port = hostpart.split("/")[0].split(":")
    db = hostpart.split("/")[1].split("?")[0]
    conn = psycopg2.connect(
        host=host,
        port=port,
        dbname=db,
        user=props["spring.datasource.username"],
        password=props["spring.datasource.password"],
        sslmode="require",
    )
    conn.set_session(readonly=True, autocommit=True)
    lines: list[str] = []
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema='inventario' AND table_name='permiso'
            ORDER BY ordinal_position
            """
        )
        lines.append("columnas_permiso: " + ", ".join(r[0] for r in cur.fetchall()))
        cur.execute(
            "SELECT recurso, accion FROM inventario.permiso WHERE recurso='proveedor' ORDER BY accion"
        )
        lines.append("catalogo_permiso_proveedor")
        for recurso, accion in cur.fetchall():
            lines.append(f"{recurso}\t{accion}")
        cur.execute(
            """
            SELECT r.nombre, p.accion
            FROM inventario.permiso p
            JOIN inventario.rol_permiso rp ON rp.id_permiso = p.id_permiso
            JOIN inventario.rol r ON r.id_rol = rp.id_rol
            WHERE p.recurso='proveedor'
            ORDER BY r.nombre, p.accion
            """
        )
        lines.append("rol_permiso_proveedor")
        for rol, accion in cur.fetchall():
            lines.append(f"{rol}\t{accion}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("OK", OUT.relative_to(ROOT))
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
