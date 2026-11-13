package com.agrofresh.dto;

public class ProveedorForm {

    private String nombre;
    private String dui;
    private String nit;
    private String correoElectronico;
    private String direccion;
    private String telefono;
    private String iva;

    public static ProveedorForm desde(ProveedorDetalleDto proveedor) {
        ProveedorForm form = new ProveedorForm();
        form.setNombre(proveedor.nombre());
        form.setDui(proveedor.dui());
        form.setNit(proveedor.nit());
        form.setCorreoElectronico(proveedor.correoElectronico());
        form.setDireccion(proveedor.direccion());
        form.setTelefono(proveedor.telefono());
        form.setIva(proveedor.iva());
        return form;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDui() { return dui; }
    public void setDui(String dui) { this.dui = dui; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getCorreoElectronico() { return correoElectronico; }
    public void setCorreoElectronico(String correoElectronico) { this.correoElectronico = correoElectronico; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getIva() { return iva; }
    public void setIva(String iva) { this.iva = iva; }
}
