package com.bodega.plataforma.semilla;

import com.bodega.plataforma.catalogo.aplicacion.comandoservicios.ProductoComandoServicio;
import com.bodega.plataforma.catalogo.dominio.modelo.agregados.Producto;
import com.bodega.plataforma.catalogo.dominio.modelo.comandos.CrearProductoComando;
import com.bodega.plataforma.clientes.aplicacion.comandoservicios.ClienteComandoServicio;
import com.bodega.plataforma.clientes.dominio.modelo.comandos.CambiarEstadoClienteComando;
import com.bodega.plataforma.clientes.dominio.modelo.comandos.CrearClienteComando;
import com.bodega.plataforma.clientes.dominio.modelo.objetosvalor.Capitalizacion;
import com.bodega.plataforma.clientes.dominio.modelo.objetosvalor.CondicionesCredito;
import com.bodega.plataforma.clientes.dominio.modelo.objetosvalor.Moneda;
import com.bodega.plataforma.clientes.dominio.modelo.objetosvalor.TasaInteres;
import com.bodega.plataforma.clientes.dominio.modelo.objetosvalor.TipoTasa;
import com.bodega.plataforma.compartido.aplicacion.resultado.ErrorAplicacion;
import com.bodega.plataforma.compartido.aplicacion.resultado.Resultado;
import com.bodega.plataforma.compartido.infraestructura.reloj.RelojSistemaImpl;
import com.bodega.plataforma.credito.aplicacion.comandoservicios.DeudaComandoServicio;
import com.bodega.plataforma.credito.aplicacion.comandoservicios.VentaCreditoComandoServicio;
import com.bodega.plataforma.credito.aplicacion.comandoservicios.VentaCreditoComandoServicio.VentaConstruida;
import com.bodega.plataforma.credito.dominio.modelo.agregados.Obligacion;
import com.bodega.plataforma.credito.dominio.modelo.comandos.CondonarObligacionComando;
import com.bodega.plataforma.credito.dominio.modelo.comandos.RegistrarPagoCicloComando;
import com.bodega.plataforma.credito.dominio.modelo.comandos.RegistrarPagoCuotaComando;
import com.bodega.plataforma.credito.dominio.modelo.comandos.RegistrarVentaComando;
import com.bodega.plataforma.credito.dominio.modelo.comandos.RegistrarVentaComando.LineaVentaComando;
import com.bodega.plataforma.credito.dominio.modelo.objetosvalor.Modalidad;
import com.bodega.plataforma.estadocuenta.aplicacion.interno.tareas.CorteAutomaticoTarea;
import com.bodega.plataforma.identidad.interfaces.acl.IdentidadContextoFachada;
import com.bodega.plataforma.tiendas.aplicacion.comandoservicios.TiendaComandoServicio;
import com.bodega.plataforma.tiendas.aplicacion.consultaservicios.TiendaConsultaServicio;
import com.bodega.plataforma.tiendas.dominio.modelo.comandos.CrearTiendaComando;
import com.bodega.plataforma.tiendas.dominio.modelo.consultas.ObtenerTodasLasTiendasConsulta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de demostración: cinco tiendas de distintos giros (dos bodegas, una panadería, una botica y una peluquería
 * y spa) con sus productos o servicios, clientes y un historial de ventas, pagos, mora y
 * condonaciones construido con la fecha del sistema (se simula el reloj hacia atrás y luego se restaura).
 * Solo se ejecuta si no existe ninguna tienda y la propiedad semilla.datos-demo está activa.
 * <p>
 * Credenciales de demostración: vendedor de Don Pepe = pepe / pepe123; de Doña Rosa = rosa / rosa123; de la panadería =
 * marta / marta123; de la botica = luis / luis123; de la peluquería = lucero / lucero123;
 * todos los clientes de demostración tienen la clave 123456.
 */
@Slf4j
@Component
public class SemillaDemostracion {

    private static final String CLAVE_CLIENTES_DEMO = "123456";

    private final boolean activa;
    private final TiendaComandoServicio tiendaComando;
    private final TiendaConsultaServicio tiendaConsulta;
    private final ProductoComandoServicio productoComando;
    private final ClienteComandoServicio clienteComando;
    private final VentaCreditoComandoServicio ventaComando;
    private final DeudaComandoServicio deudaComando;
    private final IdentidadContextoFachada identidadFachada;
    private final CorteAutomaticoTarea corteTarea;
    private final RelojSistemaImpl reloj;

    public SemillaDemostracion(@Value("${semilla.datos-demo}") boolean activa, TiendaComandoServicio tiendaComando,
                               TiendaConsultaServicio tiendaConsulta, ProductoComandoServicio productoComando,
                               ClienteComandoServicio clienteComando, VentaCreditoComandoServicio ventaComando,
                               DeudaComandoServicio deudaComando, IdentidadContextoFachada identidadFachada,
                               CorteAutomaticoTarea corteTarea, RelojSistemaImpl reloj) {
        this.activa = activa;
        this.tiendaComando = tiendaComando;
        this.tiendaConsulta = tiendaConsulta;
        this.productoComando = productoComando;
        this.clienteComando = clienteComando;
        this.ventaComando = ventaComando;
        this.deudaComando = deudaComando;
        this.identidadFachada = identidadFachada;
        this.corteTarea = corteTarea;
        this.reloj = reloj;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        if (!activa || !tiendaConsulta.manejar(new ObtenerTodasLasTiendasConsulta(false)).isEmpty()) return;
        var hoy = reloj.hoy();
        try {
            log.info("Cargando datos de demostración...");
            cargarDonPepe(hoy);
            cargarDonaRosa(hoy);
            cargarPanaderia(hoy);
            cargarBotica(hoy);
            cargarPeluqueria(hoy);
            reloj.restaurarRelojReal();
            for (var tienda : tiendaConsulta.manejar(new ObtenerTodasLasTiendasConsulta(true)))
                corteTarea.procesarTienda(tienda.getId());
            log.info("Datos de demostración cargados. Vendedores: pepe, rosa, marta, luis y lucero (clave: usuario + 123, p. ej. pepe123). Clientes: clave {}.",
                    CLAVE_CLIENTES_DEMO);
        } catch (Exception e) {
            log.error("No se pudieron cargar los datos de demostración: {}", e.getMessage(), e);
        } finally {
            reloj.restaurarRelojReal();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // La Tiendita de Don Pepe
    // ------------------------------------------------------------------------------------------------

    private void cargarDonPepe(LocalDate hoy) {
        var tiendaId = crearTienda("La Tiendita de Don Pepe", "tiendita-don-pepe", "logo-don-pepe.png", "Bodega", "20512345678",
                "Av. Los Olivos 123, Los Olivos, Lima", "987654321", "José Pérez Ramírez", "pepe", "pepe123");
        var p = productosDonPepe(tiendaId);

        var maria = cliente(tiendaId, "45871236", "María", "Quispe Rojas", "987111222", "PEN", "500", 3, 20, 26,
                efectiva("24"), efectiva("36"));
        var juan = cliente(tiendaId, "40219876", "Juan", "Huamán Cárdenas", "987222333", "PEN", "800", 6, 20, 26,
                nominal("30", Capitalizacion.MENSUAL), nominal("42", Capitalizacion.MENSUAL));
        var rosa = cliente(tiendaId, "32145698", "Rosa", "Mendoza Silva", "987333444", "PEN", "300", 3, 10, 15,
                efectiva("18"), efectiva("30"));
        var carlos = cliente(tiendaId, "41236547", "Carlos", "Ramírez Ponce", "987444555", "PEN", "600", 4, 25, 5,
                efectiva("20"), efectiva("36"));
        var lucia = cliente(tiendaId, "46587412", "Lucía", "Torres Vega", "987555666", "PEN", "150", 2, 20, 26,
                efectiva("24"), efectiva("36"));
        var pedro = cliente(tiendaId, "43698521", "Pedro", "Salazar Díaz", "987666777", "USD", "200", 3, 20, 26,
                efectiva("15"), efectiva("24"));
        var ana = cliente(tiendaId, "47521369", "Ana", "Flores Núñez", "987777888", "PEN", "400", 3, 15, 22,
                efectiva("24"), efectiva("36"));
        var miguel = cliente(tiendaId, "44789632", "Miguel", "Castro Luna", "987888999", "PEN", "700", 6, 20, 26,
                nominal("24", Capitalizacion.DIARIA), nominal("36", Capitalizacion.DIARIA));
        cliente(tiendaId, "48123654", "Elena", "Vargas Ruiz", "987999000", "PEN", "250", 2, 20, 26,
                efectiva("24"), efectiva("36"));
        var jose = cliente(tiendaId, "42365478", "José", "Paredes Alva", "987000111", "PEN", "300", 3, 20, 26,
                efectiva("24"), efectiva("36"));

        // María: una compra a fin de mes ya pagada a tiempo y otra reciente pendiente.
        var v = vender(tiendaId, maria, hoy.minusDays(75).atTime(10, 0),
                l(p, "Arroz extra 1 kg", "2", Modalidad.FIN_DE_MES, null), l(p, "Aceite vegetal 1 L", "1", Modalidad.FIN_DE_MES, null),
                l(p, "Leche evaporada tarro 400 g", "6", Modalidad.FIN_DE_MES, null));
        pagarCiclo(tiendaId, maria, v.obligaciones().get(0), 0);
        vender(tiendaId, maria, hoy.minusDays(6).atTime(11, 0),
                l(p, "Azúcar Rubia 1 kg", "3", Modalidad.FIN_DE_MES, null), l(p, "Pollo entero (kg)", "2", Modalidad.FIN_DE_MES, null),
                l(p, "Huevos de granja 1 kg", "1", Modalidad.FIN_DE_MES, null));

        // Juan: plan de 3 cuotas: la 1.ª a tiempo, la 2.ª con 9 días de mora y la 3.ª vencida sin pagar.
        v = vender(tiendaId, juan, hoy.minusDays(140).atTime(9, 30),
                l(p, "Canasta básica familiar", "1", Modalidad.CUOTAS, 3), l(p, "Detergente en polvo 2.6 kg", "1", Modalidad.CUOTAS, 3));
        var plan = v.obligaciones().get(0);
        pagarCuota(tiendaId, juan, plan, 1, plan.getCuotas().get(0).getFechaVencimiento(), hoy);
        pagarCuota(tiendaId, juan, plan, 2, plan.getCuotas().get(1).getFechaVencimiento().plusDays(9), hoy);

        // Rosa: compra a fin de mes sin pagar: con mora.
        vender(tiendaId, rosa, hoy.minusDays(70).atTime(16, 0),
                l(p, "Arroz extra 1 kg", "5", Modalidad.FIN_DE_MES, null), l(p, "Aceite vegetal 1 L", "2", Modalidad.FIN_DE_MES, null),
                l(p, "Atún en filete 170 g", "6", Modalidad.FIN_DE_MES, null));

        // Carlos: compra después de su corte (día 26) que pasa al ciclo siguiente, y una compra en 2 cuotas.
        var despuesDelCorte = hoy.minusMonths(1).withDayOfMonth(26).atTime(10, 0);
        vender(tiendaId, carlos, despuesDelCorte.isAfter(LocalDateTime.of(hoy, LocalTime.of(0, 0))) ? hoy.minusDays(40).atTime(10, 0) : despuesDelCorte,
                l(p, "Fideos spaghetti 500 g", "4", Modalidad.FIN_DE_MES, null), l(p, "Queso fresco 1 kg", "1", Modalidad.FIN_DE_MES, null));
        vender(tiendaId, carlos, hoy.minusDays(30).atTime(15, 0),
                l(p, "Pack útiles escolares", "1", Modalidad.CUOTAS, 2));

        // Lucía: cerca de su límite de S/ 150.00.
        vender(tiendaId, lucia, hoy.minusDays(8).atTime(12, 0),
                l(p, "Pollo entero (kg)", "2", Modalidad.FIN_DE_MES, null), l(p, "Queso fresco 1 kg", "1", Modalidad.FIN_DE_MES, null),
                l(p, "Aceite vegetal 1 L", "2", Modalidad.FIN_DE_MES, null), l(p, "Arroz extra 1 kg", "3", Modalidad.FIN_DE_MES, null),
                l(p, "Leche evaporada tarro 400 g", "6", Modalidad.FIN_DE_MES, null), l(p, "Yogurt natural 1 L", "2", Modalidad.FIN_DE_MES, null));

        // Pedro: crédito en dólares (tipo de cambio 3.75).
        vender(tiendaId, pedro, hoy.minusDays(15).atTime(10, 30),
                l(p, "Balón de gas 10 kg", "1", Modalidad.FIN_DE_MES, null), l(p, "Pack útiles escolares", "1", Modalidad.CUOTAS, 2));

        // Ana: una deuda condonada y otra pagada a tiempo.
        v = vender(tiendaId, ana, hoy.minusDays(60).atTime(14, 0),
                l(p, "Detergente en polvo 2.6 kg", "1", Modalidad.FIN_DE_MES, null), l(p, "Papel higiénico x4", "2", Modalidad.FIN_DE_MES, null));
        condonar(tiendaId, v.obligaciones().get(0), hoy.minusDays(50), "Ayuda social por emergencia familiar");
        v = vender(tiendaId, ana, hoy.minusDays(35).atTime(9, 0),
                l(p, "Gaseosa 1.5 L", "2", Modalidad.FIN_DE_MES, null), l(p, "Galletas de soda x6", "3", Modalidad.FIN_DE_MES, null));
        pagarCiclo(tiendaId, ana, v.obligaciones().get(0), 0);

        // Miguel: plan de 4 cuotas al día, con tasa nominal capitalizable diariamente.
        vender(tiendaId, miguel, hoy.minusDays(20).atTime(10, 0),
                l(p, "Canasta básica familiar", "1", Modalidad.CUOTAS, 4), l(p, "Balón de gas 10 kg", "1", Modalidad.CUOTAS, 4));

        // José: compra pagada y luego dado de baja.
        v = vender(tiendaId, jose, hoy.minusDays(90).atTime(10, 0), l(p, "Arroz extra 1 kg", "4", Modalidad.FIN_DE_MES, null));
        pagarCiclo(tiendaId, jose, v.obligaciones().get(0), 0);
        reloj.restaurarRelojReal();
        esperar(clienteComando.manejar(new CambiarEstadoClienteComando(tiendaId, jose, false)));
    }

    // ------------------------------------------------------------------------------------------------
    // Bodega Doña Rosa
    // ------------------------------------------------------------------------------------------------

    private void cargarDonaRosa(LocalDate hoy) {
        var tiendaId = crearTienda("Bodega Doña Rosa", "bodega-dona-rosa", "logo-dona-rosa.png", "Bodega", "20698765432",
                "Jr. Los Pinos 456, San Martín de Porres, Lima", "955123456", "Rosa Campos Díaz", "rosa", "rosa123");
        var p = productosDonaRosa(tiendaId);
        var teresa = cliente(tiendaId, "40112233", "Teresa", "Campos Lima", "955000111", "PEN", "300", 3, 18, 25,
                efectiva("22"), efectiva("34"));
        var victor = cliente(tiendaId, "41223344", "Víctor", "León Mora", "955000222", "PEN", "500", 4, 18, 25,
                efectiva("22"), efectiva("34"));
        vender(tiendaId, teresa, hoy.minusDays(10).atTime(11, 0),
                l(p, "Arroz extra 1 kg", "3", Modalidad.FIN_DE_MES, null), l(p, "Leche evaporada tarro 400 g", "4", Modalidad.FIN_DE_MES, null));
        vender(tiendaId, victor, hoy.minusDays(30).atTime(17, 0),
                l(p, "Canasta básica familiar", "1", Modalidad.CUOTAS, 3));
    }

    // ------------------------------------------------------------------------------------------------
    // Panadería La Espiga Dorada
    // ------------------------------------------------------------------------------------------------

    private void cargarPanaderia(LocalDate hoy) {
        reloj.restaurarRelojReal();
        var tiendaId = crearTienda("Panadería La Espiga Dorada", "panaderia-espiga-dorada", "logo-espiga-dorada.png",
                "Panadería", "20456789123", "Av. Universitaria 2150, Los Olivos, Lima", "944112233",
                "Marta Quispe Huamán", "marta", "marta123");
        var p = productosPanaderia(tiendaId);
        var carmen = cliente(tiendaId, "70112233", "Carmen", "Ríos Soto", "944000111", "PEN", "250", 2, 15, 22,
                efectiva("20"), efectiva("32"));
        var hugo = cliente(tiendaId, "70223344", "Hugo", "Mamani Quispe", "944000222", "PEN", "400", 3, 25, 5,
                nominal("24", Capitalizacion.MENSUAL), nominal("36", Capitalizacion.MENSUAL));
        var ines = cliente(tiendaId, "70334455", "Inés", "Cáceres Roca", "944000333", "PEN", "120", 2, 10, 18,
                efectiva("22"), efectiva("34"));
        var oscar = cliente(tiendaId, "70445566", "Óscar", "Valdivia Peña", "944000444", "PEN", "600", 3, 20, 27,
                efectiva("18"), efectiva("30"));

        // Carmen: una compra a fin de mes pagada a tiempo y otra reciente pendiente.
        var v = vender(tiendaId, carmen, hoy.minusDays(70).atTime(8, 30),
                l(p, "Pan francés (10 unidades)", "5", Modalidad.FIN_DE_MES, null),
                l(p, "Croissant de mantequilla", "4", Modalidad.FIN_DE_MES, null),
                l(p, "Keke de vainilla (porción)", "2", Modalidad.FIN_DE_MES, null));
        pagarCiclo(tiendaId, carmen, v.obligaciones().get(0), 0);
        vender(tiendaId, carmen, hoy.minusDays(6).atTime(9, 0),
                l(p, "Pan ciabatta (4 unidades)", "2", Modalidad.FIN_DE_MES, null),
                l(p, "Café pasado (vaso)", "3", Modalidad.FIN_DE_MES, null));

        // Hugo: tasa nominal con capitalización mensual y una compra sin pagar: con mora.
        vender(tiendaId, hugo, hoy.minusDays(65).atTime(16, 0),
                l(p, "Docena de empanadas surtidas", "2", Modalidad.FIN_DE_MES, null));

        // Inés: compra que deja la deuda justo en su límite de S/ 120.00.
        vender(tiendaId, ines, hoy.minusDays(5).atTime(12, 0),
                l(p, "Torta tres leches entera", "1", Modalidad.FIN_DE_MES, null),
                l(p, "Docena de empanadas surtidas", "1", Modalidad.FIN_DE_MES, null));

        // Óscar: torta de matrimonio en 3 cuotas, la 1.ª pagada a tiempo.
        v = vender(tiendaId, oscar, hoy.minusDays(100).atTime(10, 0),
                l(p, "Torta de matrimonio (50 personas)", "1", Modalidad.CUOTAS, 3));
        var plan = v.obligaciones().get(0);
        pagarCuota(tiendaId, oscar, plan, 1, plan.getCuotas().get(0).getFechaVencimiento(), hoy);
        reloj.restaurarRelojReal();
    }

    private Map<String, Long> productosPanaderia(Long tiendaId) {
        var ids = new HashMap<String, Long>();
        producto(ids, tiendaId, "Pan francés (10 unidades)", "Panes", "Producción propia", "La Espiga Dorada", "bolsa", "pan-frances.jpg", "4.00", "4.00", false);
        producto(ids, tiendaId, "Pan ciabatta (4 unidades)", "Panes", "Producción propia", "La Espiga Dorada", "bolsa", "pan-ciabatta.jpg", "5.50", "5.50", false);
        producto(ids, tiendaId, "Pan integral molde 500 g", "Panes", "Producción propia", "La Espiga Dorada", "unidad", "pan-integral.jpg", "7.80", "8.00", false);
        producto(ids, tiendaId, "Croissant de mantequilla", "Pastelería", "Producción propia", "La Espiga Dorada", "unidad", "croissant.jpg", "3.50", "3.80", false);
        producto(ids, tiendaId, "Empanada de pollo", "Salados", "Producción propia", "La Espiga Dorada", "unidad", "empanada-pollo.jpg", "4.50", "4.80", false);
        producto(ids, tiendaId, "Keke de vainilla (porción)", "Pastelería", "Producción propia", "La Espiga Dorada", "porción", "keke-vainilla.jpg", "3.00", "3.20", false);
        producto(ids, tiendaId, "Alfajor de manjar blanco", "Pastelería", "Producción propia", "La Espiga Dorada", "unidad", "alfajor.jpg", "2.50", "2.70", false);
        producto(ids, tiendaId, "Pastel de tres leches (porción)", "Pastelería", "Producción propia", "La Espiga Dorada", "porción", "torta-tres-leches.jpg", "8.50", "9.00", false);
        producto(ids, tiendaId, "Torta tres leches entera", "Tortas", "Producción propia", "La Espiga Dorada", "unidad", "torta-entera.jpg", "65.00", "70.00", false);
        producto(ids, tiendaId, "Docena de empanadas surtidas", "Salados", "Producción propia", "La Espiga Dorada", "docena", "empanadas-docena.jpg", "48.00", "50.00", false);
        producto(ids, tiendaId, "Café pasado (vaso)", "Bebidas", "Producción propia", "La Espiga Dorada", "vaso", "cafe-vaso.jpg", "3.50", "3.50", false);
        producto(ids, tiendaId, "Chicha morada 1 L", "Bebidas", "Producción propia", "La Espiga Dorada", "botella", "chicha-morada.jpg", "7.50", "7.50", false);
        producto(ids, tiendaId, "Torta de matrimonio (50 personas)", "Tortas", "Producción propia", "La Espiga Dorada", "unidad", "torta-matrimonio.jpg", "180.00", "190.00", true);
        return ids;
    }

    // ------------------------------------------------------------------------------------------------
    // Botica Salud Vecinal
    // ------------------------------------------------------------------------------------------------

    private void cargarBotica(LocalDate hoy) {
        reloj.restaurarRelojReal();
        var tiendaId = crearTienda("Botica Salud Vecinal", "botica-salud-vecinal", "logo-salud-vecinal.png", "Botica",
                "20567891234", "Jr. Las Palmeras 310, Comas, Lima", "933221144", "Luis Ramos Terrones", "luis", "luis123");
        var p = productosBotica(tiendaId);
        var elvira = cliente(tiendaId, "71112233", "Elvira", "Salas Montes", "933000111", "PEN", "300", 3, 18, 25,
                efectiva("20"), efectiva("32"));
        var jorge = cliente(tiendaId, "71223344", "Jorge", "Pinto Lazo", "933000222", "PEN", "200", 2, 20, 26,
                efectiva("22"), efectiva("34"));
        var daniela = cliente(tiendaId, "71334455", "Daniela", "Rojas Vega", "933000333", "USD", "100", 3, 15, 22,
                efectiva("12"), efectiva("20"));
        var raul = cliente(tiendaId, "71445566", "Raúl", "Medina Cruz", "933000444", "PEN", "300", 4, 20, 26,
                nominal("24", Capitalizacion.DIARIA), nominal("36", Capitalizacion.DIARIA));

        // Elvira: compra a fin de mes pagada a tiempo y un tensiómetro en 3 cuotas (la 1.ª pagada).
        var v = vender(tiendaId, elvira, hoy.minusDays(95).atTime(11, 30),
                l(p, "Tensiómetro digital de brazo", "1", Modalidad.CUOTAS, 3));
        var plan = v.obligaciones().get(0);
        v = vender(tiendaId, elvira, hoy.minusDays(80).atTime(10, 0),
                l(p, "Paracetamol 500 mg x10 tabletas", "2", Modalidad.FIN_DE_MES, null),
                l(p, "Vitamina C 500 mg x20 tabletas", "1", Modalidad.FIN_DE_MES, null));
        pagarCiclo(tiendaId, elvira, v.obligaciones().get(0), 0);
        pagarCuota(tiendaId, elvira, plan, 1, plan.getCuotas().get(0).getFechaVencimiento(), hoy);

        // Jorge: compra a fin de mes sin pagar: con mora.
        vender(tiendaId, jorge, hoy.minusDays(60).atTime(17, 0),
                l(p, "Jarabe para la tos 120 ml", "2", Modalidad.FIN_DE_MES, null),
                l(p, "Suero oral 500 ml", "3", Modalidad.FIN_DE_MES, null));

        // Daniela: crédito en dólares (tipo de cambio 3.75) con pañales en 2 cuotas.
        vender(tiendaId, daniela, hoy.minusDays(25).atTime(9, 45),
                l(p, "Pañales para bebé x30", "1", Modalidad.CUOTAS, 2),
                l(p, "Alcohol medicinal 70° 250 ml", "1", Modalidad.FIN_DE_MES, null));

        // Raúl: tasa nominal con capitalización diaria; compra grande que lo deja cerca de su límite de S/ 300.00.
        vender(tiendaId, raul, hoy.minusDays(8).atTime(15, 15),
                l(p, "Termómetro digital", "1", Modalidad.FIN_DE_MES, null),
                l(p, "Mascarillas descartables x50", "2", Modalidad.FIN_DE_MES, null),
                l(p, "Pañales para bebé x30", "5", Modalidad.FIN_DE_MES, null),
                l(p, "Gasas estériles x10", "4", Modalidad.FIN_DE_MES, null));
        reloj.restaurarRelojReal();
    }

    private Map<String, Long> productosBotica(Long tiendaId) {
        var ids = new HashMap<String, Long>();
        producto(ids, tiendaId, "Paracetamol 500 mg x10 tabletas", "Analgésicos", "Genérico", "Droguería local", "blíster", "paracetamol.jpg", "3.50", "3.50", false);
        producto(ids, tiendaId, "Ibuprofeno 400 mg x10 tabletas", "Analgésicos", "Genérico", "Droguería local", "blíster", "ibuprofeno.jpg", "5.00", "5.00", false);
        producto(ids, tiendaId, "Vitamina C 500 mg x20 tabletas", "Vitaminas", "Genérico", "Droguería local", "frasco", "vitamina-c.jpg", "14.00", "14.50", false);
        producto(ids, tiendaId, "Alcohol medicinal 70° 250 ml", "Primeros auxilios", "Genérico", "Droguería local", "botella", "alcohol-medicinal.jpg", "5.50", "5.50", false);
        producto(ids, tiendaId, "Algodón hidrófilo 100 g", "Primeros auxilios", "Genérico", "Droguería local", "bolsa", "algodon.jpg", "4.50", "4.50", false);
        producto(ids, tiendaId, "Gasas estériles x10", "Primeros auxilios", "Genérico", "Droguería local", "paquete", "gasas.jpg", "6.00", "6.00", false);
        producto(ids, tiendaId, "Curitas adhesivas x20", "Primeros auxilios", "Genérico", "Droguería local", "caja", "curitas.jpg", "4.00", "4.00", false);
        producto(ids, tiendaId, "Termómetro digital", "Equipos", "Genérico", "Importadora médica", "unidad", "termometro-digital.jpg", "22.00", "24.00", false);
        producto(ids, tiendaId, "Mascarillas descartables x50", "Protección", "Genérico", "Importadora médica", "caja", "mascarillas.jpg", "15.00", "15.00", false);
        producto(ids, tiendaId, "Suero oral 500 ml", "Hidratación", "Genérico", "Droguería local", "botella", "suero-oral.jpg", "4.50", "4.50", false);
        producto(ids, tiendaId, "Jarabe para la tos 120 ml", "Resfríos", "Genérico", "Droguería local", "frasco", "jarabe-tos.jpg", "12.00", "12.50", false);
        producto(ids, tiendaId, "Pañales para bebé x30", "Bebés", "Genérico", "Distribuidora local", "paquete", "panales.jpg", "38.00", "40.00", true);
        producto(ids, tiendaId, "Tensiómetro digital de brazo", "Equipos", "Genérico", "Importadora médica", "unidad", "tensiometro.jpg", "120.00", "130.00", true);
        return ids;
    }

    // ------------------------------------------------------------------------------------------------
    // Peluquería y Spa Estilo Lucero (servicios)
    // ------------------------------------------------------------------------------------------------

    private void cargarPeluqueria(LocalDate hoy) {
        reloj.restaurarRelojReal();
        var tiendaId = crearTienda("Peluquería y Spa Estilo Lucero", "peluqueria-estilo-lucero", "logo-estilo-lucero.png",
                "Peluquería y spa", "20678912345", "Av. Túpac Amaru 890, Independencia, Lima", "922334455",
                "Lucero Vega Paredes", "lucero", "lucero123");
        var p = productosPeluqueria(tiendaId);
        var patricia = cliente(tiendaId, "72112233", "Patricia", "Villanueva Ríos", "922000111", "PEN", "350", 3, 20, 27,
                efectiva("20"), efectiva("32"));
        var gloria = cliente(tiendaId, "72223344", "Gloria", "Sánchez Mori", "922000222", "PEN", "500", 4, 15, 22,
                nominal("30", Capitalizacion.MENSUAL), nominal("42", Capitalizacion.MENSUAL));
        var rocio = cliente(tiendaId, "72334455", "Rocío", "Benavides Luna", "922000333", "PEN", "250", 2, 10, 18,
                efectiva("22"), efectiva("34"));
        var fiorella = cliente(tiendaId, "72445566", "Fiorella", "Díaz Campos", "922000444", "USD", "300", 4, 20, 26,
                efectiva("15"), efectiva("24"));

        // Patricia: servicios a fin de mes, uno pagado a tiempo y otro reciente pendiente.
        var v = vender(tiendaId, patricia, hoy.minusDays(75).atTime(15, 0),
                l(p, "Corte de cabello dama", "1", Modalidad.FIN_DE_MES, null),
                l(p, "Manicure clásica", "1", Modalidad.FIN_DE_MES, null));
        pagarCiclo(tiendaId, patricia, v.obligaciones().get(0), 0);
        vender(tiendaId, patricia, hoy.minusDays(7).atTime(11, 0),
                l(p, "Tinte completo", "1", Modalidad.FIN_DE_MES, null),
                l(p, "Peinado de fiesta", "1", Modalidad.FIN_DE_MES, null));

        // Gloria: alisado orgánico en 3 cuotas, la 1.ª a tiempo y la 2.ª con 6 días de mora.
        v = vender(tiendaId, gloria, hoy.minusDays(120).atTime(16, 30),
                l(p, "Alisado orgánico", "1", Modalidad.CUOTAS, 3));
        var plan = v.obligaciones().get(0);
        pagarCuota(tiendaId, gloria, plan, 1, plan.getCuotas().get(0).getFechaVencimiento(), hoy);
        pagarCuota(tiendaId, gloria, plan, 2, plan.getCuotas().get(1).getFechaVencimiento().plusDays(6), hoy);

        // Rocío: servicios a fin de mes sin pagar: con mora.
        vender(tiendaId, rocio, hoy.minusDays(62).atTime(13, 0),
                l(p, "Corte de cabello dama", "2", Modalidad.FIN_DE_MES, null),
                l(p, "Limpieza facial profunda", "1", Modalidad.FIN_DE_MES, null));

        // Fiorella: crédito en dólares; paquete de novia en 4 cuotas con periodo de gracia.
        vender(tiendaId, fiorella, hoy.minusDays(18).atTime(10, 0),
                l(p, "Paquete de novia (prueba y día del evento)", "1", Modalidad.CUOTAS, 4),
                l(p, "Maquillaje social", "1", Modalidad.FIN_DE_MES, null));
        reloj.restaurarRelojReal();
    }

    private Map<String, Long> productosPeluqueria(Long tiendaId) {
        var ids = new HashMap<String, Long>();
        producto(ids, tiendaId, "Corte de cabello dama", "Cabello", "Servicio del salón", "Estilo Lucero", "servicio", "corte-dama.jpg", "35.00", "38.00", false);
        producto(ids, tiendaId, "Corte de cabello caballero", "Cabello", "Servicio del salón", "Estilo Lucero", "servicio", "corte-caballero.jpg", "20.00", "22.00", false);
        producto(ids, tiendaId, "Tinte completo", "Color", "Servicio del salón", "Estilo Lucero", "servicio", "tinte.jpg", "90.00", "95.00", false);
        producto(ids, tiendaId, "Mechas o balayage", "Color", "Servicio del salón", "Estilo Lucero", "servicio", "mechas.jpg", "220.00", "240.00", true);
        producto(ids, tiendaId, "Peinado de fiesta", "Cabello", "Servicio del salón", "Estilo Lucero", "servicio", "peinado.jpg", "60.00", "65.00", false);
        producto(ids, tiendaId, "Alisado orgánico", "Tratamientos", "Servicio del salón", "Estilo Lucero", "servicio", "alisado.jpg", "280.00", "300.00", true);
        producto(ids, tiendaId, "Tratamiento capilar de keratina", "Tratamientos", "Servicio del salón", "Estilo Lucero", "sesión", "keratina.jpg", "120.00", "130.00", true);
        producto(ids, tiendaId, "Manicure clásica", "Uñas", "Servicio del salón", "Estilo Lucero", "servicio", "manicure.jpg", "25.00", "27.00", false);
        producto(ids, tiendaId, "Pedicure spa", "Uñas", "Servicio del salón", "Estilo Lucero", "servicio", "pedicure.jpg", "40.00", "43.00", false);
        producto(ids, tiendaId, "Masaje relajante 60 min", "Spa", "Servicio del salón", "Estilo Lucero", "sesión", "masaje.jpg", "90.00", "95.00", false);
        producto(ids, tiendaId, "Limpieza facial profunda", "Spa", "Servicio del salón", "Estilo Lucero", "sesión", "limpieza-facial.jpg", "70.00", "75.00", false);
        producto(ids, tiendaId, "Maquillaje social", "Maquillaje", "Servicio del salón", "Estilo Lucero", "servicio", "maquillaje.jpg", "80.00", "85.00", false);
        producto(ids, tiendaId, "Paquete de novia (prueba y día del evento)", "Paquetes", "Servicio del salón", "Estilo Lucero", "paquete", "paquete-novia.jpg", "650.00", "700.00", true);
        return ids;
    }

    // ------------------------------------------------------------------------------------------------
    // Catálogos
    // ------------------------------------------------------------------------------------------------

    private Map<String, Long> productosDonPepe(Long tiendaId) {
        var ids = new HashMap<String, Long>();
        producto(ids, tiendaId, "Arroz extra 1 kg", "Abarrotes", "Marca propia", "Distribuidora local", "kg", "arroz-extra.jpg", "4.20", "4.20", false);
        producto(ids, tiendaId, "Azúcar Rubia 1 kg", "Abarrotes", "Cartavio", "Cartavio", "kg", "azucar-rubia.jpg", "3.90", "3.90", false);
        producto(ids, tiendaId, "Aceite vegetal 1 L", "Abarrotes", "Marca propia", "Distribuidora local", "litro", "aceite-vegetal.jpg", "9.50", "9.50", false);
        producto(ids, tiendaId, "Fideos spaghetti 500 g", "Abarrotes", "Marca propia", "Distribuidora local", "paquete", "fideos-spaghetti.jpg", "3.20", "3.20", false);
        producto(ids, tiendaId, "Lentejas 500 g", "Abarrotes", "Bells", "Bells", "paquete", "lentejas.jpg", "4.80", "4.80", false);
        producto(ids, tiendaId, "Avena en hojuelas 250 g", "Abarrotes", "Marca propia", "Distribuidora local", "paquete", "avena-hojuelas.jpg", "2.80", "2.80", false);
        producto(ids, tiendaId, "Atún en filete 170 g", "Conservas", "Marca propia", "Distribuidora local", "lata", "atun-filete.jpg", "5.50", "5.50", false);
        producto(ids, tiendaId, "Leche evaporada tarro 400 g", "Lácteos", "Marca propia", "Distribuidora local", "tarro", "leche-evaporada.jpg", "4.30", "4.30", false);
        producto(ids, tiendaId, "Yogurt natural 1 L", "Lácteos", "Marca propia", "Distribuidora local", "botella", "yogurt-natural.jpg", "7.20", "7.20", false);
        producto(ids, tiendaId, "Queso fresco 1 kg", "Lácteos", "Artesanal", "Granja local", "kg", "queso-fresco.jpg", "26.00", "27.00", false);
        producto(ids, tiendaId, "Huevos de granja 1 kg", "Frescos", "Marca propia", "Distribuidora local", "kg", "huevos-granja.jpg", "8.50", "8.50", false);
        producto(ids, tiendaId, "Pollo entero (kg)", "Frescos", "San Fernando", "San Fernando", "kg", "pollo-entero.jpg", "12.50", "12.50", false);
        producto(ids, tiendaId, "Pan francés (10 unidades)", "Panadería", "Panadería del barrio", "Panadería del barrio", "bolsa", "pan-frances.jpg", "4.00", "4.00", false);
        producto(ids, tiendaId, "Gaseosa 1.5 L", "Bebidas", "Marca propia", "Distribuidora local", "botella", "gaseosa-1-5l.jpg", "6.50", "6.50", false);
        producto(ids, tiendaId, "Gaseosa cola 2 L", "Bebidas", "Marca propia", "Distribuidora local", "botella", "gaseosa-cola-2l.jpg", "8.50", "8.50", false);
        producto(ids, tiendaId, "Agua mineral 625 ml", "Bebidas", "Marca propia", "Distribuidora local", "botella", "agua-mineral.jpg", "1.50", "1.50", false);
        producto(ids, tiendaId, "Cerveza rubia 620 ml", "Licores", "Marca propia", "Distribuidora local", "botella", "cerveza-rubia.jpg", "6.00", "6.00", false);
        producto(ids, tiendaId, "Galletas de soda x6", "Snacks", "Marca propia", "Distribuidora local", "paquete", "galletas-soda.jpg", "3.60", "3.60", false);
        producto(ids, tiendaId, "Papas fritas clásicas 150 g", "Snacks", "Marca propia", "Distribuidora local", "bolsa", "papas-fritas.jpg", "6.50", "6.50", false);
        producto(ids, tiendaId, "Chocolate con maní", "Snacks", "Marca propia", "Distribuidora local", "unidad", "chocolate-mani.jpg", "1.80", "1.80", false);
        producto(ids, tiendaId, "Detergente en polvo 2.6 kg", "Limpieza", "Marca propia", "Distribuidora local", "bolsa", "detergente-polvo.jpg", "24.90", "26.00", true);
        producto(ids, tiendaId, "Lejía 1 L", "Limpieza", "Marca propia", "Distribuidora local", "botella", "lejia.jpg", "4.50", "4.50", false);
        producto(ids, tiendaId, "Jabón de lavar barra", "Limpieza", "Marca propia", "Distribuidora local", "unidad", "jabon-lavar.jpg", "2.20", "2.20", false);
        producto(ids, tiendaId, "Papel higiénico x4", "Higiene", "Marca propia", "Distribuidora local", "paquete", "papel-higienico.jpg", "6.80", "6.80", false);
        producto(ids, tiendaId, "Shampoo anticaspa 375 ml", "Higiene", "Marca propia", "Distribuidora local", "frasco", "shampoo-anticaspa.jpg", "18.90", "19.90", true);
        producto(ids, tiendaId, "Pasta dental 75 ml", "Higiene", "Marca propia", "Distribuidora local", "tubo", "pasta-dental.jpg", "5.90", "5.90", false);
        producto(ids, tiendaId, "Recarga de agua bidón 20 L", "Bebidas", "Agua de mesa", "Distribuidora local", "bidón", "bidon-agua.jpg", "9.00", "9.00", false);
        producto(ids, tiendaId, "Balón de gas 10 kg", "Gas", "Solgas", "Solgas", "balón", "balon-gas.jpg", "58.00", "60.00", true);
        producto(ids, tiendaId, "Canasta básica familiar", "Combos", "Combo Don Pepe", "Don Pepe", "canasta", "canasta-basica.jpg", "120.00", "125.00", true);
        producto(ids, tiendaId, "Pack útiles escolares", "Bazar", "Faber-Castell", "Distribuidora escolar", "pack", "pack-utiles.jpg", "45.00", "48.00", true);
        return ids;
    }

    private Map<String, Long> productosDonaRosa(Long tiendaId) {
        var ids = new HashMap<String, Long>();
        producto(ids, tiendaId, "Arroz extra 1 kg", "Abarrotes", "Marca propia", "Distribuidora local", "kg", "arroz-extra.jpg", "4.30", "4.30", false);
        producto(ids, tiendaId, "Azúcar Rubia 1 kg", "Abarrotes", "Cartavio", "Cartavio", "kg", "azucar-rubia.jpg", "4.00", "4.00", false);
        producto(ids, tiendaId, "Aceite vegetal 1 L", "Abarrotes", "Marca propia", "Distribuidora local", "litro", "aceite-vegetal.jpg", "9.80", "9.80", false);
        producto(ids, tiendaId, "Fideos spaghetti 500 g", "Abarrotes", "Marca propia", "Distribuidora local", "paquete", "fideos-spaghetti.jpg", "3.30", "3.30", false);
        producto(ids, tiendaId, "Leche evaporada tarro 400 g", "Lácteos", "Marca propia", "Distribuidora local", "tarro", "leche-evaporada.jpg", "4.40", "4.40", false);
        producto(ids, tiendaId, "Huevos de granja 1 kg", "Frescos", "Marca propia", "Distribuidora local", "kg", "huevos-granja.jpg", "8.80", "8.80", false);
        producto(ids, tiendaId, "Gaseosa 1.5 L", "Bebidas", "Marca propia", "Distribuidora local", "botella", "gaseosa-1-5l.jpg", "6.60", "6.60", false);
        producto(ids, tiendaId, "Galletas de soda x6", "Snacks", "Marca propia", "Distribuidora local", "paquete", "galletas-soda.jpg", "3.70", "3.70", false);
        producto(ids, tiendaId, "Detergente en polvo 2.6 kg", "Limpieza", "Marca propia", "Distribuidora local", "bolsa", "detergente-polvo.jpg", "25.50", "26.50", true);
        producto(ids, tiendaId, "Papel higiénico x4", "Higiene", "Marca propia", "Distribuidora local", "paquete", "papel-higienico.jpg", "7.00", "7.00", false);
        producto(ids, tiendaId, "Balón de gas 10 kg", "Gas", "Solgas", "Solgas", "balón", "balon-gas.jpg", "59.00", "61.00", true);
        producto(ids, tiendaId, "Canasta básica familiar", "Combos", "Combo Doña Rosa", "Doña Rosa", "canasta", "canasta-basica.jpg", "118.00", "123.00", true);
        return ids;
    }

    // ------------------------------------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------------------------------------

    private Long crearTienda(String nombre, String slug, String logo, String giro, String ruc, String direccion,
                             String telefono, String dueno, String usuario, String clave) {
        var tienda = esperar(tiendaComando.manejar(new CrearTiendaComando(nombre, slug, logo, giro, ruc, direccion, telefono,
                new BigDecimal("3.7500"), dueno, usuario, clave)));
        return tienda.getId();
    }

    private void producto(Map<String, Long> ids, Long tiendaId, String nombre, String categoria, String marca,
                          String proveedor, String unidad, String imagen, String contado, String credito, boolean cuotas) {
        Producto p = esperar(productoComando.manejar(new CrearProductoComando(tiendaId, nombre, categoria, marca, proveedor,
                nombre, unidad, imagen, new BigDecimal(contado), new BigDecimal(credito), cuotas)));
        ids.put(nombre, p.getId());
    }

    private Long cliente(Long tiendaId, String dni, String nombres, String apellidos, String telefono, String moneda,
                         String limite, int maxMeses, int corte, int pago, TasaInteres compensatoria, TasaInteres mora) {
        var condiciones = new CondicionesCredito(Moneda.valueOf(moneda), new BigDecimal(limite), maxMeses, corte,
                LocalTime.of(23, 59), pago, compensatoria, mora);
        var creado = esperar(clienteComando.manejar(new CrearClienteComando(tiendaId, dni, nombres, apellidos, telefono,
                "Lima", condiciones)));
        identidadFachada.cambiarClave(creado.cliente().getUsuarioId(), CLAVE_CLIENTES_DEMO);
        return creado.cliente().getId();
    }

    private static TasaInteres efectiva(String porcentaje) {
        return new TasaInteres(TipoTasa.EFECTIVA, new BigDecimal(porcentaje), null);
    }

    private static TasaInteres nominal(String porcentaje, Capitalizacion capitalizacion) {
        return new TasaInteres(TipoTasa.NOMINAL, new BigDecimal(porcentaje), capitalizacion);
    }

    private static LineaVentaComando l(Map<String, Long> productos, String nombre, String cantidad, Modalidad modalidad,
                                       Integer cuotas) {
        var id = productos.get(nombre);
        if (id == null) throw new IllegalStateException("Producto de demostración inexistente: " + nombre);
        return new LineaVentaComando(id, new BigDecimal(cantidad), modalidad, cuotas);
    }

    private VentaConstruida vender(Long tiendaId, Long clienteId, LocalDateTime fechaHora, LineaVentaComando... lineas) {
        reloj.simular(fechaHora);
        return esperar(ventaComando.manejar(new RegistrarVentaComando(tiendaId, clienteId, List.of(lineas))));
    }

    private void pagarCiclo(Long tiendaId, Long clienteId, Obligacion obligacion, int diasDespuesDelVencimiento) {
        var fecha = obligacion.getFechaPago().plusDays(diasDespuesDelVencimiento);
        reloj.simular(fecha.atTime(12, 0));
        esperar(deudaComando.manejar(new RegistrarPagoCicloComando(tiendaId, clienteId, obligacion.getFechaPago(), fecha)));
    }

    /** Paga una cuota en la fecha indicada solo si esa fecha ya pasó respecto de hoy. */
    private void pagarCuota(Long tiendaId, Long clienteId, Obligacion plan, int numero, LocalDate fecha, LocalDate hoy) {
        if (fecha.isAfter(hoy)) return;
        reloj.simular(fecha.atTime(12, 0));
        esperar(deudaComando.manejar(new RegistrarPagoCuotaComando(tiendaId, plan.getId(), numero, fecha)));
    }

    private void condonar(Long tiendaId, Obligacion obligacion, LocalDate fecha, String motivo) {
        reloj.simular(fecha.atTime(12, 0));
        esperar(deudaComando.manejar(new CondonarObligacionComando(tiendaId, obligacion.getId(), motivo)));
    }

    private static <T> T esperar(Resultado<T, ErrorAplicacion> resultado) {
        if (resultado instanceof Resultado.Exito<T, ErrorAplicacion> exito) return exito.valor();
        var error = ((Resultado.Fallo<T, ErrorAplicacion>) resultado).error();
        throw new IllegalStateException("%s: %s".formatted(error.mensaje(), error.detalle()));
    }
}
