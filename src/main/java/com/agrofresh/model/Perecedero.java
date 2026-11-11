package com.agrofresh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "perecedero", schema = "inventario")
public class Perecedero {

    @Id
    @Column(name = "id_producto", nullable = false, updatable = false)
    private Integer idProducto;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "id_producto", nullable = false, updatable = false)
    private Producto producto;

    @Column(name = "temp_recomendad", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperaturaRecomendada;

    @Column(name = "vida_util", nullable = false)
    private Integer vidaUtil;

    protected Perecedero() {
        // Requerido por JPA.
    }

    public Perecedero(Producto producto, BigDecimal temperaturaRecomendada, Integer vidaUtil) {
        this.producto = producto;
        this.temperaturaRecomendada = temperaturaRecomendada;
        this.vidaUtil = vidaUtil;
    }

    public void actualizar(BigDecimal temperaturaRecomendada, Integer vidaUtil) {
        this.temperaturaRecomendada = temperaturaRecomendada;
        this.vidaUtil = vidaUtil;
    }

    public Integer getIdProducto() { return idProducto; }
    public Producto getProducto() { return producto; }
    public BigDecimal getTemperaturaRecomendada() { return temperaturaRecomendada; }
    public Integer getVidaUtil() { return vidaUtil; }
}
