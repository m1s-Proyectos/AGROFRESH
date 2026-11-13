(() => {
  const form = document.getElementById('form-movimiento');
  if (!form) return;
  const el = id => document.getElementById(id);
  const bodega = el('bodega');
  const producto = el('producto');
  const lote = el('lote');
  const cantidad = el('cantidad');
  const motivo = el('motivo');
  const estado = el('catalogo-estado');
  const disponible = el('disponible');
  const loteField = el('lote-field');
  const lotesInfo = el('lotes-info');
  const lotesInfoList = el('lotes-info-list');
  const confirmar = el('confirmar');
  const resultado = el('resultado-operacion');
  const endpoint = form.dataset.endpoint;
  const tipo = form.dataset.tipo;
  const loteMod = form.dataset.loteMod;
  const conMotivo = form.dataset.motivo === 'true';
  const permitido = form.dataset.permitido === 'true';
  let bodegas = [];
  let productos = [];
  let lotes = [];
  let version = 0;
  let enviando = false;

  const show = (mensaje, clase = '') => {
    estado.textContent = mensaje;
    estado.className = 'catalogo-estado' + (clase ? ' catalogo-' + clase : '');
  };
  const fill = (select, rows, placeholder, label) => {
    select.replaceChildren(new Option(placeholder, ''));
    rows.forEach(row => select.add(new Option(label(row), String(row.id))));
    select.disabled = rows.length === 0;
  };
  const stockSeleccionado = () => {
    if (loteMod === 'stock' || loteMod === 'vencido') {
      return lotes.find(l => String(l.id) === lote.value)?.stock;
    }
    return productos.find(p => String(p.id) === producto.value)?.stock;
  };
  const muestraResultado = (mensaje, ok, idMovimiento) => {
    if (!resultado) return;
    resultado.replaceChildren();
    resultado.appendChild(document.createTextNode(mensaje));
    if (ok && idMovimiento != null && loteMod !== 'info') {
      const enlace = document.createElement('a');
      enlace.href = '/movimientos/consultar/' + idMovimiento;
      enlace.className = 'alert-link ms-2';
      enlace.textContent = 'Ver detalle del movimiento';
      resultado.appendChild(enlace);
    }
    if (ok && idMovimiento != null && loteMod === 'info') {
      const nota = document.createElement('span');
      nota.className = 'd-block mt-2 small';
      nota.textContent = 'Consulta el historial filtrando por salidas; puede haber varios movimientos.';
      resultado.appendChild(nota);
    }
    resultado.className = 'alert ' + (ok ? 'alert-success' : 'alert-danger');
    resultado.classList.remove('d-none');
  };
  function updateButton() {
    const stock = Number(stockSeleccionado());
    const n = Number(cantidad.value);
    const loteOk = loteMod === 'stock' || loteMod === 'vencido' ? !!lote.value : true;
    const motivoOk = !conMotivo || (motivo && motivo.value.trim().length > 0);
    confirmar.disabled = !permitido || enviando || !bodega.value || !producto.value
      || !loteOk || !motivoOk
      || !Number.isInteger(n) || n <= 0
      || !Number.isFinite(stock) || n > stock;
  }
  const clearQuantity = () => {
    cantidad.value = '';
    cantidad.max = '';
    cantidad.disabled = true;
    disponible.textContent = 'Selecciona un producto para consultar existencias.';
    updateButton();
  };
  const clearProduct = () => {
    productos = [];
    lotes = [];
    fill(producto, [], 'Selecciona primero una bodega', () => '');
    if (lote) fill(lote, [], 'Selecciona un lote', () => '');
    if (lotesInfoList) lotesInfoList.replaceChildren();
    if (lotesInfo) lotesInfo.hidden = loteMod !== 'info';
    clearQuantity();
    if (conMotivo && motivo) motivo.disabled = true;
  };
  async function getJson(path) {
    const response = await fetch(path, { headers: { Accept: 'application/json' } });
    if (!response.ok) {
      throw new Error(response.status === 403
        ? 'No tienes autorización para esta operación.'
        : response.status === 503
          ? 'La conexión a PostgreSQL no está configurada.'
          : 'No se pudieron cargar los datos (' + response.status + ').');
    }
    return response.json();
  }
  async function loadProducts() {
    const request = ++version;
    clearProduct();
    if (!bodega.value) return;
    show('Consultando productos con existencias…');
    try {
      const rows = await getJson('/api/movimientos/productos?bodega='
        + encodeURIComponent(bodega.value) + '&tipo=' + encodeURIComponent(tipo));
      if (request !== version) return;
      productos = rows;
      fill(producto, rows, rows.length ? 'Selecciona un producto' : 'Sin productos con stock',
        r => r.nombre + ' · ' + r.codigo_interno + ' (' + r.stock + ' disponibles)');
      show(rows.length ? 'Productos cargados.' : 'No hay productos de este tipo con stock en la bodega.');
    } catch (e) {
      if (request === version) show(e.message, 'error');
    }
    updateButton();
  }
  function setStockFromProduct() {
    const item = productos.find(p => String(p.id) === producto.value);
    if (!item) return;
    if (loteMod === 'none' || loteMod === 'info') {
      cantidad.max = item.stock;
      cantidad.disabled = false;
      disponible.textContent = 'Disponible en bodega: ' + item.stock + ' unidades.';
      if (conMotivo && motivo) motivo.disabled = false;
    }
    updateButton();
  }
  async function loadLotesExtra() {
    const request = ++version;
    lotes = [];
    if (lote) fill(lote, [], 'Selecciona un lote', () => '');
    clearQuantity();
    if (!bodega.value || !producto.value) return;
    if (loteMod === 'info') {
      setStockFromProduct();
      show('Consultando lotes vigentes…');
      try {
        const rows = await getJson('/api/movimientos/lotes-disponibles?bodega='
          + encodeURIComponent(bodega.value) + '&producto=' + encodeURIComponent(producto.value));
        if (request !== version) return;
        if (lotesInfoList) {
          lotesInfoList.replaceChildren();
          rows.forEach(r => {
            const li = document.createElement('li');
            li.textContent = 'Lote ' + r.id + ' · vence ' + r.vencimiento + ' · ' + r.stock + ' u.';
            lotesInfoList.appendChild(li);
          });
        }
        show(rows.length ? 'Listo para registrar la salida.' : 'No hay lotes vigentes; revisa existencias.');
      } catch (e) {
        if (request === version) show(e.message, 'error');
      }
      return;
    }
    if (loteMod !== 'stock' && loteMod !== 'vencido') {
      setStockFromProduct();
      return;
    }
    const path = loteMod === 'vencido'
      ? '/api/movimientos/lotes-vencidos?bodega=' + encodeURIComponent(bodega.value)
        + '&producto=' + encodeURIComponent(producto.value)
      : '/api/movimientos/lotes-con-stock?bodega=' + encodeURIComponent(bodega.value)
        + '&producto=' + encodeURIComponent(producto.value);
    show(loteMod === 'vencido' ? 'Consultando lotes vencidos…' : 'Consultando lotes…');
    try {
      const rows = await getJson(path);
      if (request !== version) return;
      lotes = rows;
      fill(lote, rows, rows.length ? 'Selecciona un lote' : 'No hay lotes aplicables',
        r => 'Lote ' + r.id + ' · vence ' + r.vencimiento + ' · ' + r.stock + ' disponibles');
      if (conMotivo && motivo) motivo.disabled = rows.length === 0;
      show(rows.length ? 'Lotes cargados.' : 'No hay lotes que cumplan los criterios.');
    } catch (e) {
      if (request === version) show(e.message, 'error');
    }
    updateButton();
  }
  if (loteField) {
    loteField.hidden = loteMod !== 'stock' && loteMod !== 'vencido';
    if (lote) lote.required = loteMod === 'stock' || loteMod === 'vencido';
  }
  if (lotesInfo) lotesInfo.hidden = loteMod !== 'info';
  bodega.addEventListener('change', loadProducts);
  producto.addEventListener('change', loadLotesExtra);
  cantidad.addEventListener('input', updateButton);
  if (motivo) motivo.addEventListener('input', updateButton);
  if (lote) {
    lote.addEventListener('change', () => {
      clearQuantity();
      const item = lotes.find(l => String(l.id) === lote.value);
      if (item) {
        cantidad.max = item.stock;
        cantidad.disabled = false;
        disponible.textContent = 'Disponible en este lote: ' + item.stock + ' unidades.';
        if (conMotivo && motivo) motivo.disabled = false;
      }
      updateButton();
    });
  }
  form.addEventListener('submit', async e => {
    e.preventDefault();
    updateButton();
    if (confirmar.disabled || enviando) return;
  const etiqueta = conMotivo ? 'baja' : 'salida';
    if (!window.confirm('¿Confirmar la ' + etiqueta + ' de ' + cantidad.value + ' unidades?')) return;
    enviando = true;
    updateButton();
    show('Registrando operación en PostgreSQL…');
    resultado?.classList.add('d-none');
    const payload = {
      bodega: Number(bodega.value),
      producto: Number(producto.value),
      cantidad: Number(cantidad.value)
    };
    if (conMotivo) payload.motivo = motivo.value.trim();
    if (loteMod === 'stock' || loteMod === 'vencido') payload.numLote = Number(lote.value);
    try {
      const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
      const csrf = document.querySelector('meta[name="_csrf"]');
      const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
      if (csrf && csrfHeader) headers[csrfHeader.content] = csrf.content;
      const response = await fetch(endpoint, { method: 'POST', headers, body: JSON.stringify(payload) });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.mensaje || 'No se pudo completar la operación (' + response.status + ').');
      }
      let texto = body.mensaje || 'Operación registrada.';
      if (body.idMovimiento != null) {
        texto += ' Referencia de movimiento: ' + body.idMovimiento + '.';
        if (loteMod === 'info') {
          texto += ' Consulta el historial; pueden existir varios movimientos para esta salida.';
        }
      }
      muestraResultado(texto, true, body.idMovimiento);
      show('Operación registrada.', 'success');
      await loadProducts();
    } catch (error) {
      muestraResultado(error.message, false, null);
      show(error.message, 'error');
    } finally {
      enviando = false;
      updateButton();
    }
  });
  getJson('/api/movimientos/bodegas').then(rows => {
    bodegas = rows;
    fill(bodega, rows, 'Selecciona una bodega', b => b.nombre + ' — ' + b.ubicacion);
    show(rows.length ? 'Selecciona la bodega.' : 'No hay bodegas registradas.');
    if (!permitido) show('No tienes permiso para confirmar esta operación.');
  }).catch(e => show(e.message + ' Comprueba tu conexión PostgreSQL.', 'error'));
})();
