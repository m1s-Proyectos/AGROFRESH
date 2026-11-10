package com.agrofresh.dto;

/** Form input deliberately omits the database-generated category identifier. */
public class CategoriaForm {

    private String nombre;
    private String descripcion;

    public CategoriaForm() {
    }

    public CategoriaForm(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
