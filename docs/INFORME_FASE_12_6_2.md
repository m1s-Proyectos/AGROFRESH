# Informe Fase 12.6.2 — Formularios Thymeleaf (salidas y bajas)

**Fecha:** 2026-11-10  
**Proyecto:** AGROFRESH  
**Alcance:** Interfaces web en el módulo Movimientos. Sin cambios en PostgreSQL ni en servicios 12.6.1.

### Leyenda

| Marca | Significado |
|---|---|
| **VERIFICADO** | Confirmado en código o pruebas Maven. |
| **IMPLEMENTADO** | Entregado en esta fase. |
| **PENDIENTE** | Requiere entorno aislado o fase posterior. |
| **RIESGO** | Limitación conocida para el usuario o la UI. |

---

## 1. Objetivo — **IMPLEMENTADO**

Exponer cinco formularios en Thymeleaf que consumen los endpoints REST de la Fase 12.6.1, con catálogos, CSRF, permisos y mensajes de éxito/error, integrados al historial de movimientos.

---

## 2. Arquitectura inspeccionada — **VERIFICADO**

| Referencia | Uso en 12.6.2 |
|---|---|
| `traslados.html` + `TrasladoCatalogoController` | Patrón fetch JSON, CSRF en meta + headers, confirmación, flag `enviando` |
| `MovimientoOperacionController` | POST sin cambios |
| `MovimientoSalidaSolicitud` / `MovimientoBajaSolicitud` | Cuerpo JSON del formulario |
| `SecurityConfig` | Ampliado solo con GET de formularios |
| `MovimientoService` / historial | Navegación y enlaces post-operación |

---

## 3. Vistas creadas — **IMPLEMENTADO**

| Plantilla |
|---|
| `movimiento-salida-no-perecedero.html` |
| `movimiento-salida-perecedero.html` |
| `movimiento-baja-dano-no-perecedero.html` |
| `movimiento-baja-dano-perecedero.html` |
| `movimiento-baja-vencimiento.html` |
| `fragments/movimientos-submenu.html` |
| `fragments/movimiento-operacion-form.html` |
| `static/js/movimiento-operacion.js` |

---

## 4. Vistas modificadas — **IMPLEMENTADO**

| Archivo | Cambio |
|---|---|
| `movimientos.html` | Submenú de operaciones y enlaces a salidas |
| `static/css/inventario.css` | Estilos del submenú e información de lotes |

---

## 5. Formularios implementados — **IMPLEMENTADO**

| Formulario | Ruta GET | Lote en UI |
|---|---|---|
| Salida NP | `/movimientos/salida/no-perecedero` | No |
| Salida PER | `/movimientos/salida/perecedero` | Solo consulta (FEFO en PG) |
| Baja daño NP | `/movimientos/baja/dano/no-perecedero` | No |
| Baja daño PER | `/movimientos/baja/dano/perecedero` | Selector obligatorio |
| Baja vencimiento | `/movimientos/baja/vencimiento` | Solo vencidos con stock |

---

## 6. Correspondencia con DTO — **VERIFICADO**

**Salida** (`MovimientoSalidaSolicitud`):

```json
{ "bodega": number, "producto": number, "cantidad": number }
```

**Baja** (`MovimientoBajaSolicitud`):

```json
{ "bodega": number, "producto": number, "cantidad": number, "motivo": string, "numLote": number | omitido (NP) }
```

`id_usuario` nunca se envía desde el cliente.

---

## 7. Endpoints consumidos — **IMPLEMENTADO**

| Operación | POST |
|---|---|
| Salida NP/PER | `/api/movimientos/salida/...` (12.6.1) |
| Bajas | `/api/movimientos/baja/...` (12.6.1) |

**Catálogos (solo lectura, nuevo controlador):**

| GET | Uso |
|---|---|
| `/api/movimientos/bodegas` | Todas las formas |
| `/api/movimientos/productos?bodega&tipo` | NP / PER |
| `/api/movimientos/lotes-disponibles?bodega&producto` | Información salida PER |
| `/api/movimientos/lotes-con-stock?bodega&producto` | Baja daño PER |
| `/api/movimientos/lotes-vencidos?bodega&producto` | Baja vencimiento |

---

## 8. Catálogos reutilizados — **VERIFICADO**

Consultas alineadas con `TrasladoCatalogoController` (productos, `fn_lotes_disponibles`) y con el criterio de `LotesController` para lotes vencidos. **No** se duplicó lógica FEFO en JavaScript.

---

## 9. Implementación CSRF — **IMPLEMENTADO**

Meta `_csrf` / `_csrf_header` en cada formulario; cabecera en `fetch` POST (igual que traslados). Pruebas de controller 12.6.1 ya validaban CSRF en API.

---

## 10. Restricciones de permisos — **IMPLEMENTADO**

| Capa | Salidas | Bajas |
|---|---|---|
| GET formulario | `movimiento.INSERT` | `ROLE_ADMIN` |
| POST API | Idem 12.6.1 | Idem |
| Submenú | Visible si `puedeRegistrarSalida` / `esAdministrador` | — |

Ocultar enlaces no sustituye la autorización del servidor.

---

## 11. Manejo de errores — **IMPLEMENTADO**

- Éxito: alerta Bootstrap + mensaje y `idMovimiento` como referencia.
- Error: `mensaje` del JSON (`P0001` → 400) o texto genérico en 403/500.
- Sin stack traces ni SQL en pantalla.
- Flag `enviando` + `confirm()` evita doble envío.

**Salida perecedera — RIESGO:** texto explícito de que pueden generarse **varios** movimientos y que `idMovimiento` es solo referencia del primero; se invita a revisar el historial.

---

## 12. Pruebas ejecutadas — **VERIFICADO**

| Prueba | Resultado |
|---|---|
| `mvn test` (completo) | OK |
| `MovimientoFormViewsTest` | GET salida (operador), GET baja denegada, GET baja admin, GET salida sin INSERT |
| Regresión 12.6.1 (`MovimientoOperacion*`) | OK |

**PENDIENTE:** prueba manual/E2E con catálogos reales en entorno aislado; simulación de `P0001` en navegador.

---

## 13. Limitaciones detectadas — **RIESGO / PENDIENTE**

1. El historial **no agrupa** movimientos de una misma salida perecedera; no hay vínculo en BD expuesto a la UI.
2. Catálogos dependen de JDBC activo (mismo comportamiento que traslados si no hay conexión).
3. Prueba de integración POST real contra Supabase **no ejecutada** (restricción de fase).

---

## 14. Pendientes Fase 12.6.3 (recomendación) — **PENDIENTE**

- Smoke test manual en entorno de pruebas.
- Enlace «Ver detalle» tras éxito cuando `idMovimiento` sea único (bajas / salida NP).
- Documentación de usuario operativo (filtros historial por tipo SALIDA / bajas).
- Evaluación de reporte o agrupación de salidas perecederas multilote (solo diseño; sin cambiar SP).

---

## Cierre

| Criterio | Estado |
|---|---|
| Cinco formularios en módulo Movimientos | **Sí** |
| PostgreSQL sin cambios | **Sí** |
| Sin escritura en inventario real en pruebas | **Sí** |
| Servicios 12.6.1 sin cambios funcionales | **Sí** |

**DETENIDO** en 12.6.2. No avanzar a 12.6.3 sin autorización.
