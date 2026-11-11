package com.agrofresh.service.impl;

import com.agrofresh.dto.CategoriaDto;
import com.agrofresh.dto.ClasificacionProducto;
import com.agrofresh.dto.ProductoDetalleDto;
import com.agrofresh.dto.ProductoForm;
import com.agrofresh.dto.ProductoListadoDto;
import com.agrofresh.exception.BusinessException;
import com.agrofresh.exception.ResourceNotFoundException;
import com.agrofresh.mapper.ProductoMapper;
import com.agrofresh.model.NoPerecedero;
import com.agrofresh.model.Perecedero;
import com.agrofresh.model.Producto;
import com.agrofresh.repository.NoPerecederoRepository;
import com.agrofresh.repository.PerecederoRepository;
import com.agrofresh.repository.ProductoRepository;
import com.agrofresh.service.CategoriaService;
import com.agrofresh.service.ProductoService;
import com.agrofresh.util.UnidadMedidaCatalog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@Transactional(readOnly = true)
public class ProductoServiceImpl implements ProductoService {
    private static final BigDecimal MAX_TEMPERATURA = new BigDecimal("999.99");

    private final ProductoRepository productos;
    private final PerecederoRepository perecederos;
    private final NoPerecederoRepository noPerecederos;
    private final CategoriaService categorias;
    private final ProductoMapper mapper;

    public ProductoServiceImpl(
            ProductoRepository productos,
            PerecederoRepository perecederos,
            NoPerecederoRepository noPerecederos,
            CategoriaService categorias,
            ProductoMapper mapper) {
        this.productos = productos;
        this.perecederos = perecederos;
        this.noPerecederos = noPerecederos;
        this.categorias = categorias;
        this.mapper = mapper;
    }

    @Override
    public List<ProductoListadoDto> listar() {
        try {
            return productos.findAllByOrderByNombreAsc().stream().map(producto -> {
                CategoriaDto categoria = categorias.consultar(producto.getIdCategoria());
                return mapper.toListado(producto, categoria, clasificacionExacta(producto.getIdProducto()));
            }).toList();
        } catch (BusinessException | ResourceNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudieron consultar los productos.", exception);
        }
    }

    @Override
    public ProductoDetalleDto consultar(Integer idProducto) {
        validarIdentificador(idProducto);
        try {
            Producto producto = buscarProducto(idProducto);
            return detalle(producto);
        } catch (BusinessException | ResourceNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo consultar el producto.", exception);
        }
    }

    @Override
    @Transactional
    public ProductoDetalleDto registrar(ProductoForm form) {
        DatosProducto datos = validar(form, null);
        try {
            if (productos.existsByCodigoInterno(datos.codigoInterno())) {
                throw new BusinessException("Ya existe un producto con ese código interno.");
            }
            Producto producto = productos.saveAndFlush(new Producto(
                    datos.idCategoria(), datos.codigoInterno(), datos.nombre(), datos.unidadMedida()));
            guardarClasificacion(producto, datos);
            return detalle(producto);
        } catch (BusinessException | ResourceNotFoundException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("No se pudo guardar el producto porque sus datos incumplen una restricción.", exception);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo guardar el producto. Inténtalo nuevamente.", exception);
        }
    }

    @Override
    @Transactional
    public ProductoDetalleDto actualizar(Integer idProducto, ProductoForm form) {
        validarIdentificador(idProducto);
        try {
            Producto producto = buscarProducto(idProducto);
            DatosProducto datos = validar(form, producto.getUnidadMedida());
            if (productos.existsByCodigoInternoAndIdProductoNot(datos.codigoInterno(), idProducto)) {
                throw new BusinessException("Ya existe otro producto con ese código interno.");
            }

            ClasificacionProducto actual = clasificacionExacta(idProducto);
            if (actual != datos.clasificacion()) {
                validarCambioClasificacion(idProducto);
            }

            producto.actualizar(
                    datos.idCategoria(), datos.codigoInterno(), datos.nombre(), datos.unidadMedida());
            productos.saveAndFlush(producto);
            actualizarClasificacion(producto, actual, datos);
            return detalle(producto);
        } catch (BusinessException | ResourceNotFoundException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("No se pudo actualizar el producto porque sus datos incumplen una restricción.", exception);
        } catch (DataAccessException exception) {
            throw new BusinessException("No se pudo actualizar el producto. Inténtalo nuevamente.", exception);
        }
    }

    @Override
    public List<CategoriaDto> categoriasDisponibles() {
        return categorias.listar();
    }

    @Override
    public List<String> unidadesDisponibles() {
        return UnidadMedidaCatalog.OPCIONES;
    }

    private DatosProducto validar(ProductoForm form, String unidadActual) {
        if (form == null) {
            throw new BusinessException("Los datos del producto son obligatorios.");
        }
        String codigo = textoObligatorio(form.getCodigoInterno(), "El código interno es obligatorio.");
        if (codigo.length() > 20) {
            throw new BusinessException("El código interno no puede superar 20 caracteres.");
        }
        String nombre = textoObligatorio(form.getNombre(), "El nombre del producto es obligatorio.");
        if (nombre.length() > 150) {
            throw new BusinessException("El nombre no puede superar 150 caracteres.");
        }
        if (form.getIdCategoria() == null || form.getIdCategoria() <= 0) {
            throw new BusinessException("Debe seleccionar una categoría válida.");
        }
        categorias.consultar(form.getIdCategoria());

        String unidad = textoObligatorio(form.getUnidadMedida(), "La unidad de medida es obligatoria.");
        boolean conservaHistorica = unidadActual != null && unidad.equals(unidadActual);
        if (!conservaHistorica && !UnidadMedidaCatalog.esValida(unidad)) {
            throw new BusinessException("La unidad de medida seleccionada no es válida.");
        }
        if (unidad.length() > 20) {
            throw new BusinessException("La unidad de medida no puede superar 20 caracteres.");
        }
        if (form.getClasificacion() == null) {
            throw new BusinessException("Debe seleccionar una clasificación.");
        }

        validarClasificacion(form);
        return new DatosProducto(codigo, nombre, form.getIdCategoria(), unidad, form.getClasificacion(),
                form.getTemperaturaRecomendada(), form.getVidaUtil(),
                form.getFechaFabricacion(), form.getVidaUtilAnaquel());
    }

    private static void validarClasificacion(ProductoForm form) {
        if (form.getClasificacion() == ClasificacionProducto.PERECEDERO) {
            if (form.getFechaFabricacion() != null || form.getVidaUtilAnaquel() != null) {
                throw new BusinessException("Un producto no puede tener datos de ambas clasificaciones.");
            }
            BigDecimal temperatura = form.getTemperaturaRecomendada();
            if (temperatura == null) {
                throw new BusinessException("La temperatura recomendada es obligatoria.");
            }
            if (temperatura.scale() > 2 || temperatura.abs().compareTo(MAX_TEMPERATURA) > 0) {
                throw new BusinessException("La temperatura no es compatible con el formato numeric(5,2).");
            }
            if (form.getVidaUtil() == null || form.getVidaUtil() <= 0) {
                throw new BusinessException("La vida útil debe ser mayor que cero.");
            }
        } else {
            if (form.getTemperaturaRecomendada() != null || form.getVidaUtil() != null) {
                throw new BusinessException("Un producto no puede tener datos de ambas clasificaciones.");
            }
            if (form.getFechaFabricacion() == null) {
                throw new BusinessException("La fecha de fabricación es obligatoria.");
            }
            if (form.getFechaFabricacion().isAfter(LocalDate.now())) {
                throw new BusinessException("La fecha de fabricación no puede ser futura.");
            }
            if (form.getVidaUtilAnaquel() == null || form.getVidaUtilAnaquel() <= 0) {
                throw new BusinessException("La vida útil en anaquel debe ser mayor que cero.");
            }
        }
    }

    private void guardarClasificacion(Producto producto, DatosProducto datos) {
        if (datos.clasificacion() == ClasificacionProducto.PERECEDERO) {
            perecederos.saveAndFlush(new Perecedero(producto, datos.temperaturaRecomendada(), datos.vidaUtil()));
        } else {
            noPerecederos.saveAndFlush(
                    new NoPerecedero(producto, datos.fechaFabricacion(), datos.vidaUtilAnaquel()));
        }
    }

    private void actualizarClasificacion(
            Producto producto, ClasificacionProducto actual, DatosProducto datos) {
        Integer id = producto.getIdProducto();
        if (actual == datos.clasificacion()) {
            if (actual == ClasificacionProducto.PERECEDERO) {
                Perecedero subtipo = perecederos.findById(id)
                        .orElseThrow(() -> inconsistencia(id));
                subtipo.actualizar(datos.temperaturaRecomendada(), datos.vidaUtil());
                perecederos.saveAndFlush(subtipo);
            } else {
                NoPerecedero subtipo = noPerecederos.findById(id)
                        .orElseThrow(() -> inconsistencia(id));
                subtipo.actualizar(datos.fechaFabricacion(), datos.vidaUtilAnaquel());
                noPerecederos.saveAndFlush(subtipo);
            }
            return;
        }
        if (actual == ClasificacionProducto.PERECEDERO) {
            perecederos.deleteById(id);
            perecederos.flush();
        } else {
            noPerecederos.deleteById(id);
            noPerecederos.flush();
        }
        guardarClasificacion(producto, datos);
    }

    private void validarCambioClasificacion(Integer idProducto) {
        if (productos.tieneLotes(idProducto)) {
            throw new BusinessException("No se puede cambiar la clasificación porque el producto tiene lotes asociados.");
        }
        if (productos.tieneMovimientos(idProducto)) {
            throw new BusinessException("No se puede cambiar la clasificación porque el producto tiene movimientos asociados.");
        }
        if (productos.tieneInventario(idProducto)) {
            throw new BusinessException("No se puede cambiar la clasificación porque el producto tiene registros de inventario.");
        }
        if (productos.tieneCompras(idProducto)) {
            throw new BusinessException("No se puede cambiar la clasificación porque el producto tiene compras asociadas.");
        }
        if (productos.tieneCatalogo(idProducto)) {
            throw new BusinessException("No se puede cambiar la clasificación porque el producto está asociado a un catálogo.");
        }
    }

    private ProductoDetalleDto detalle(Producto producto) {
        Integer id = producto.getIdProducto();
        Perecedero perecedero = perecederos.findById(id).orElse(null);
        NoPerecedero noPerecedero = noPerecederos.findById(id).orElse(null);
        ClasificacionProducto clasificacion = clasificacionExacta(perecedero, noPerecedero, id);
        return mapper.toDetalle(producto, categorias.consultar(producto.getIdCategoria()),
                clasificacion, perecedero, noPerecedero);
    }

    private ClasificacionProducto clasificacionExacta(Integer idProducto) {
        return clasificacionExacta(
                perecederos.findById(idProducto).orElse(null),
                noPerecederos.findById(idProducto).orElse(null),
                idProducto);
    }

    private static ClasificacionProducto clasificacionExacta(
            Perecedero perecedero, NoPerecedero noPerecedero, Integer idProducto) {
        if ((perecedero == null) == (noPerecedero == null)) {
            throw inconsistencia(idProducto);
        }
        return perecedero != null
                ? ClasificacionProducto.PERECEDERO
                : ClasificacionProducto.NO_PERECEDERO;
    }

    private Producto buscarProducto(Integer idProducto) {
        return productos.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
    }

    private static BusinessException inconsistencia(Integer idProducto) {
        return new BusinessException(
                "El producto " + idProducto + " no tiene una clasificación única y requiere revisión.");
    }

    private static String textoObligatorio(String valor, String mensaje) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new BusinessException(mensaje);
        }
        return valor.trim();
    }

    private static void validarIdentificador(Integer idProducto) {
        if (idProducto == null || idProducto <= 0) {
            throw new ResourceNotFoundException("El producto solicitado no existe.");
        }
    }

    private record DatosProducto(
            String codigoInterno,
            String nombre,
            Integer idCategoria,
            String unidadMedida,
            ClasificacionProducto clasificacion,
            BigDecimal temperaturaRecomendada,
            Integer vidaUtil,
            LocalDate fechaFabricacion,
            Integer vidaUtilAnaquel) {
    }
}
