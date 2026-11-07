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
        name = "usuario",
        schema = "inventario",
        uniqueConstraints = @UniqueConstraint(name = "uq_usuario_username", columnNames = "username"))
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "contrasena", nullable = false, length = 150)
    private String contrasena;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Column(name = "correo", nullable = false, length = 100)
    private String correo;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "rol", length = 100)
    private String rol;

    protected Usuario() {
        // Requerido por JPA.
    }

    public Usuario(
            String username,
            String contrasena,
            String nombre,
            String apellido,
            String correo,
            String rol) {
        this.username = username;
        this.contrasena = contrasena;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.rol = rol;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getUsername() {
        return username;
    }

    public String getContrasena() {
        return contrasena;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getCorreo() {
        return correo;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getRol() {
        return rol;
    }
}
