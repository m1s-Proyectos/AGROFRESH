package com.agrofresh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "proveedor",
        schema = "inventario",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_proveedor_dui", columnNames = "dui"),
            @UniqueConstraint(name = "uq_proveedor_nit", columnNames = "nit")
        })
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor", nullable = false, updatable = false)
    private Integer idProveedor;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "dui", length = 10)
    private String dui;

    @Column(name = "nit", nullable = false, length = 17)
    private String nit;

    @Column(name = "correo_electronico", nullable = false, length = 100)
    private String correoElectronico;

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @Column(name = "telefono", nullable = false, length = 9)
    private String telefono;

    @Column(name = "iva", nullable = false, length = 50)
    private String iva;

    protected Proveedor() {
        // Requerido por JPA.
    }

    public Proveedor(
            String nombre,
            String dui,
            String nit,
            String correoElectronico,
            String direccion,
            String telefono,
            String iva) {
        this.nombre = nombre;
        this.dui = dui;
        this.nit = nit;
        this.correoElectronico = correoElectronico;
        this.direccion = direccion;
        this.telefono = telefono;
        this.iva = iva;
    }

    public void actualizar(
            String nombre,
            String dui,
            String nit,
            String correoElectronico,
            String direccion,
            String telefono,
            String iva) {
        this.nombre = nombre;
        this.dui = dui;
        this.nit = nit;
        this.correoElectronico = correoElectronico;
        this.direccion = direccion;
        this.telefono = telefono;
        this.iva = iva;
    }

    public Integer getIdProveedor() { return idProveedor; }
    public String getNombre() { return nombre; }
    public String getDui() { return dui; }
    public String getNit() { return nit; }
    public String getCorreoElectronico() { return correoElectronico; }
    public String getDireccion() { return direccion; }
    public String getTelefono() { return telefono; }
    public String getIva() { return iva; }
}
