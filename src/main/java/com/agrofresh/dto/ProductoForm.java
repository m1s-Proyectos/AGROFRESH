package com.agrofresh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductoForm {
    private String codigoInterno;
    private String nombre;
    private Integer idCategoria;
    private String unidadMedida;
    private ClasificacionProducto clasificacion;
    private BigDecimal temperaturaRecomendada;
    private Integer vidaUtil;
    private LocalDate fechaFabricacion;
    private Integer vidaUtilAnaquel;

    public ProductoForm() {
    }

    public static ProductoForm desde(ProductoDetalleDto producto) {
        ProductoForm form = new ProductoForm();
        form.codigoInterno = producto.codigoInterno();
        form.nombre = producto.nombre();
        form.idCategoria = producto.idCategoria();
        form.unidadMedida = producto.unidadMedida();
        form.clasificacion = producto.clasificacion();
        form.temperaturaRecomendada = producto.temperaturaRecomendada();
        form.vidaUtil = producto.vidaUtil();
        form.fechaFabricacion = producto.fechaFabricacion();
        form.vidaUtilAnaquel = producto.vidaUtilAnaquel();
        return form;
    }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Integer getIdCategoria() { return idCategoria; }
    public void setIdCategoria(Integer idCategoria) { this.idCategoria = idCategoria; }
    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    public ClasificacionProducto getClasificacion() { return clasificacion; }
    public void setClasificacion(ClasificacionProducto clasificacion) { this.clasificacion = clasificacion; }
    public BigDecimal getTemperaturaRecomendada() { return temperaturaRecomendada; }
    public void setTemperaturaRecomendada(BigDecimal temperaturaRecomendada) { this.temperaturaRecomendada = temperaturaRecomendada; }
    public Integer getVidaUtil() { return vidaUtil; }
    public void setVidaUtil(Integer vidaUtil) { this.vidaUtil = vidaUtil; }
    public LocalDate getFechaFabricacion() { return fechaFabricacion; }
    public void setFechaFabricacion(LocalDate fechaFabricacion) { this.fechaFabricacion = fechaFabricacion; }
    public Integer getVidaUtilAnaquel() { return vidaUtilAnaquel; }
    public void setVidaUtilAnaquel(Integer vidaUtilAnaquel) { this.vidaUtilAnaquel = vidaUtilAnaquel; }
}
