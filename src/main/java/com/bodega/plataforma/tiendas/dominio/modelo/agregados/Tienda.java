package com.bodega.plataforma.tiendas.dominio.modelo.agregados;

import com.bodega.plataforma.compartido.dominio.modelo.agregados.RaizAgregadoDominio;
import com.bodega.plataforma.tiendas.dominio.modelo.comandos.CrearTiendaComando;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Raíz de agregado Tienda: una bodega (o comercio) que otorga crédito a sus vecinos.
 * Su baja es lógica: se desactiva, nunca se elimina.
 */
@Getter
public class Tienda extends RaizAgregadoDominio<Tienda> {

    @Setter
    private Long id;
    @Setter
    private String nombre;
    @Setter
    private String slug;
    @Setter
    private String logo;
    /** Giro del negocio (Bodega, Panadería, Botica, Peluquería y spa…). Es solo una etiqueta: no cambia ningún cálculo. */
    @Setter
    private String giro;
    @Setter
    private String ruc;
    @Setter
    private String direccion;
    @Setter
    private String telefono;
    @Setter
    private BigDecimal tipoCambioUsd;
    @Setter
    private boolean activa = true;
    @Setter
    private String nombreDueno;
    @Setter
    private Long vendedorUsuarioId;
    @Setter
    private String usuarioVendedor;

    public Tienda() {
    }

    public Tienda(CrearTiendaComando comando) {
        this.nombre = comando.nombre();
        this.slug = comando.slug();
        this.logo = comando.logo();
        this.giro = comando.giro();
        this.ruc = comando.ruc();
        this.direccion = comando.direccion();
        this.telefono = comando.telefono();
        this.tipoCambioUsd = comando.tipoCambioUsd();
        this.nombreDueno = comando.nombreDueno();
        this.usuarioVendedor = comando.usuarioVendedor();
        this.activa = true;
    }

    public Tienda actualizarInformacion(String nombre, String logo, String giro, String ruc, String direccion,
                                        String telefono, BigDecimal tipoCambioUsd, String nombreDueno) {
        this.nombre = nombre;
        this.logo = logo;
        this.giro = giro;
        this.ruc = ruc;
        this.direccion = direccion;
        this.telefono = telefono;
        this.tipoCambioUsd = tipoCambioUsd;
        this.nombreDueno = nombreDueno;
        return this;
    }

    public void cambiarEstado(boolean activa) {
        this.activa = activa;
    }
}
