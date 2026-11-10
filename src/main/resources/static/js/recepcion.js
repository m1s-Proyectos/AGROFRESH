(() => {
    'use strict';
    const el = id => document.getElementById(id);
    const proveedor=el('proveedor'), bodega=el('bodega'), buscar=el('buscar'), producto=el('producto');
    const cantidad=el('cantidad'), vencimiento=el('vencimiento'), fechaCampo=el('fecha-campo');
    const agregar=el('agregar'), confirmar=el('confirmar'), estado=el('estado'), lineas=el('lineas');
    let productos=[], detalle=[], enviando=false;
    const dinero = n => '$'+Number(n).toFixed(2);
    const texto = value => String(value ?? '');
    const option = (value,label) => { const o=document.createElement('option');o.value=value;o.textContent=label;return o; };
    const mensaje = (msg,tipo='') => {estado.textContent=msg;estado.className='catalogo-estado'+(tipo==='error'?' catalogo-error':tipo==='success'?' catalogo-success':'');};
    async function getJson(url, opts={}) {
        const r=await fetch(url,{credentials:'same-origin',...opts});
        if (r.redirected && r.url.includes('/login')) throw Error('La sesión terminó. Vuelve a iniciar sesión.');
        const body=await r.json().catch(()=>null);
        if (!r.ok) throw Error((body && body.mensaje) || 'Error de conexión ('+r.status+').');
        return body;
    }
    function cargarSelect(sel,rows,prompt,format) {
        sel.replaceChildren(option('',prompt));
        for (const r of rows) sel.append(option(r.id,format(r)));
        sel.disabled=rows.length===0;
    }
    function resetFormulario() { detalle=[]; cantidad.value=''; vencimiento.value=''; dibujar(); }
    function dibujarProductos() {
        const filtro=buscar.value.trim().toLowerCase();
        const elegibles=productos.filter(p=> !detalle.some(d=>d.producto===Number(p.id)) &&
            (texto(p.nombre).toLowerCase().includes(filtro)||texto(p.codigo_interno).toLowerCase().includes(filtro)));
        cargarSelect(producto,elegibles,'Selecciona un producto',p=>p.nombre+' · '+p.codigo_interno+' · '+dinero(p.precio));
        cambiarProducto();
    }
    function cambiarProducto(){
        const p=productos.find(p=>Number(p.id)===Number(producto.value));
        const perecedero=!!p && p.tipo==='PERECEDERO';
        fechaCampo.hidden=!perecedero; vencimiento.required=perecedero;
        el('info-tipo').textContent=!p?'Selecciona un producto para conocer sus requisitos.':perecedero?
            'Perecedero: indica una fecha de vencimiento posterior a hoy.':
            'No perecedero: fabricación '+(p.fabricacion||'sin dato')+'; vida útil '+(p.vida_util||'sin dato')+' (datos ya registrados).';
        cantidad.disabled=!p; agregar.disabled=!p||!puedeRegistrar;
    }
    function dibujar(){
        lineas.replaceChildren();
        if (!detalle.length){const tr=lineas.insertRow();const td=tr.insertCell();td.colSpan=5;td.textContent='Aún no has agregado productos.';td.className='muted-row';}
        for (const [index,d] of detalle.entries()) {
            const tr=lineas.insertRow();
            const td=tr.insertCell();td.textContent=d.nombre;
            const small=document.createElement('small'); small.textContent=d.tipo==='PERECEDERO'?'Lote nuevo · vence '+d.vencimiento:'No perecedero';td.append(small);
            tr.insertCell().textContent=d.cantidad;
            tr.insertCell().textContent=dinero(d.precio);
            tr.insertCell().textContent=dinero(d.precio*d.cantidad);
            const b=document.createElement('button');b.type='button';b.className='remove';b.textContent='Quitar';b.disabled=enviando;
            b.addEventListener('click',()=>{detalle.splice(index,1);dibujar();dibujarProductos();});tr.insertCell().append(b);
        }
        el('total').textContent=dinero(detalle.reduce((sum,d)=>sum+Number(d.precio)*d.cantidad,0));
        confirmar.disabled=enviando||!puedeRegistrar||!detalle.length||!proveedor.value||!bodega.value;
    }
    proveedor.addEventListener('change',async()=>{
        resetFormulario(); productos=[]; producto.replaceChildren(option('','Cargando productos…'));producto.disabled=true;
        if (!proveedor.value){ buscar.disabled=true;mensaje('Selecciona un proveedor.');return; }
        buscar.disabled=true;buscar.value='';
        try { productos=await getJson('/api/compras/productos?proveedor='+encodeURIComponent(proveedor.value));
            buscar.disabled=false; dibujarProductos(); mensaje(productos.length?'Productos del proveedor cargados.':'El proveedor no tiene productos clasificados en catálogo.'); }
        catch(e){mensaje(e.message,'error');}
    });
    bodega.addEventListener('change',dibujar);
    buscar.addEventListener('input',dibujarProductos);
    producto.addEventListener('change',cambiarProducto);
    agregar.addEventListener('click',()=>{
        const p=productos.find(p=>Number(p.id)===Number(producto.value));
        const n=Number(cantidad.value);
        if (!p||!Number.isSafeInteger(n)||n<=0){mensaje('Selecciona producto y cantidad entera positiva.','error');return;}
        const fecha=p.tipo==='PERECEDERO'?vencimiento.value:null;
        const hoy=new Date().toLocaleDateString('en-CA',{timeZone:'America/El_Salvador'});
        if (p.tipo==='PERECEDERO'&&(!fecha||fecha<=hoy)){mensaje('Indica fecha de vencimiento posterior a hoy.','error');return;}
        if(detalle.some(d=>d.producto===Number(p.id))){mensaje('No puedes repetir un producto dentro de la misma compra.','error');return;}
        detalle.push({producto:Number(p.id),nombre:p.nombre,tipo:p.tipo,cantidad:n,precio:Number(p.precio),vencimiento:fecha});
        cantidad.value='';vencimiento.value='';dibujar();dibujarProductos();mensaje('Producto agregado. Puedes añadir otro o confirmar la compra.');
    });
    confirmar.addEventListener('click',async()=>{
        if(confirmar.disabled)return;
        if(!window.confirm('¿Registrar la compra completa con '+detalle.length+' producto(s)?'))return;
        enviando=true;dibujar();mensaje('Registrando la compra…');
        try {
            const headers={'Content-Type':'application/json'};
            const token=document.querySelector('meta[name="_csrf"]');
            const header=document.querySelector('meta[name="_csrf_header"]');
            if(token&&header)headers[header.content]=token.content;
            const data={proveedor:Number(proveedor.value),bodega:Number(bodega.value),
                lineas:detalle.map(d=>({producto:d.producto,cantidad:d.cantidad,vencimiento:d.vencimiento}))};
            const result=await getJson('/api/compras/confirmar',{method:'POST',headers,body:JSON.stringify(data)});
            resetFormulario();dibujarProductos();mensaje((result.mensaje||'Compra registrada.')+' Compra N.º '+result.idCompra+'.','success');
        }catch(e){mensaje(e.message,'error');}
        finally{enviando=false;dibujar();}
    });
    Promise.all([getJson('/api/compras/bodegas'),getJson('/api/compras/proveedores')])
        .then(([b,p])=>{
            cargarSelect(bodega,b,'Selecciona la bodega',x=>x.nombre+' — '+x.ubicacion);
            cargarSelect(proveedor,p,'Selecciona el proveedor',x=>x.nombre);
            if(!puedeRegistrar)mensaje('Puedes consultar la pantalla, pero no tienes permiso para registrar compras.');
            else mensaje('Selecciona proveedor y bodega para iniciar una compra.');
        }).catch(e=>mensaje(e.message+' Verifica la conexión PostgreSQL.','error'));
})();
