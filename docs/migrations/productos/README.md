# Migraciones propuestas — módulo Productos (Etapa 10.6)

**Estado:** scripts preparados; **ninguno ejecutado** en Supabase.

| ID | Archivo | Obligatoriedad |
|---|---|---|
| — | *(ninguna)* | No se requiere cambio estructural para operar el módulo con la configuración actual |
| MIG-10.6-OPT-01 | `MIG-10.6-OPT-01_search_path.sql` | Opcional — endurecimiento de `search_path` para SQL nativo |
| MIG-10.6-OPT-02 | `MIG-10.6-OPT-02_clasificacion_exclusiva.sql` | Opcional — exclusividad perecedero/no perecedero a nivel BD |

Herramientas de auditoría (solo lectura, no migraciones):

- `audit_readonly_queries.sql` — catálogo de consultas.
- `_run_audit_readonly.py` — ejecutor local; genera `_audit_output.txt` (artefacto local; no incluir credenciales ni datos identificables en commits).
