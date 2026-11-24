package com.bodega.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integración sobre la API real (servidor completo, base H2 y datos de demostración): seguridad por
 * rol y por tienda, y un recorrido de extremo a extremo con el reloj simulado: venta a fin de mes, rechazo por
 * límite de crédito, mora (moratorio y compensatorio de atraso, F34 a F37 del libro), bloqueo de nuevas ventas y
 * pago con prelación. Los valores esperados se calcularon aparte con aritmética decimal y redondeo por paso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlujoCompletoIntegracionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String DNI_MARIA = "45871236";
    private static final String DNI_ELENA = "48123654";

    @Value("${local.server.port}")
    private int puerto;

    private HttpResponse<String> enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var constructor = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json");
        if (token != null) constructor.header("Authorization", "Bearer " + token);
        constructor.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));
        return HTTP.send(constructor.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> respuesta) throws Exception {
        return JSON.readTree(respuesta.body());
    }

    private JsonNode iniciarSesion(String usuario, String clave) throws Exception {
        var r = enviar("POST", "/api/v1/autenticacion/iniciar-sesion", null,
                "{\"usuario\":\"%s\",\"clave\":\"%s\"}".formatted(usuario, clave));
        assertEquals(200, r.statusCode(), "inicio de sesión de " + usuario);
        return json(r);
    }

    private String token(String usuario, String clave) throws Exception {
        return iniciarSesion(usuario, clave).get("token").asText();
    }

    private long idCliente(String token, String dni) throws Exception {
        for (var c : json(enviar("GET", "/api/v1/clientes", token, null)))
            if (dni.equals(c.get("dni").asText())) return c.get("id").asLong();
        throw new AssertionError("No existe el cliente con DNI " + dni);
    }

    private static void assertDinero(String esperado, JsonNode real, String mensaje) {
        assertTrue(new BigDecimal(esperado).compareTo(real.decimalValue()) == 0,
                mensaje + ": esperado " + esperado + " pero fue " + real);
    }

    private void simularReloj(String adminToken, String fechaHora) throws Exception {
        assertEquals(200, enviar("PUT", "/api/v1/reloj", adminToken, "{\"fecha\":\"%s\"}".formatted(fechaHora)).statusCode());
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    @Order(1)
    @DisplayName("Sin sesión no se accede a la API (401)")
    void sinSesionEsRechazado() throws Exception {
        assertEquals(401, enviar("GET", "/api/v1/clientes", null, null).statusCode());
        assertEquals(401, enviar("GET", "/api/v1/clientes", "token-invalido", null).statusCode());
    }

    @Test
    @Order(2)
    @DisplayName("Un cliente no puede usar las funciones del vendedor ni del administrador (403)")
    void clienteNoUsaFuncionesDelVendedor() throws Exception {
        var pepe = iniciarSesion("pepe", "pepe123");
        var cliente = enviar("POST", "/api/v1/autenticacion/cliente", null,
                "{\"tiendaId\":%d,\"dni\":\"%s\",\"clave\":\"123456\"}".formatted(pepe.get("tiendaId").asLong(), DNI_MARIA));
        assertEquals(200, cliente.statusCode());
        var tokenCliente = json(cliente).get("token").asText();
        assertEquals("CLIENTE", json(cliente).get("rol").asText());
        assertEquals(403, enviar("GET", "/api/v1/clientes", tokenCliente, null).statusCode());
        assertEquals(403, enviar("POST", "/api/v1/ventas", tokenCliente, "{\"clienteId\":1,\"lineas\":[]}").statusCode());
        assertEquals(403, enviar("PUT", "/api/v1/reloj", tokenCliente, "{\"fecha\":\"2027-01-01T10:00:00\"}").statusCode());
    }

    @Test
    @Order(3)
    @DisplayName("Un vendedor solo ve y consulta los clientes de su propia tienda")
    void aislamientoEntreTiendas() throws Exception {
        var tokenPepe = token("pepe", "pepe123");
        var tokenRosa = token("rosa", "rosa123");
        long idMaria = idCliente(tokenPepe, DNI_MARIA);

        var deRosa = enviar("GET", "/api/v1/clientes", tokenRosa, null).body();
        assertFalse(deRosa.contains(DNI_MARIA), "la bodega de Doña Rosa no ve clientes de Don Pepe");
        assertEquals(404, enviar("GET", "/api/v1/clientes/" + idMaria, tokenRosa, null).statusCode());
        assertEquals(404, enviar("GET", "/api/v1/clientes/" + idMaria + "/deuda", tokenRosa, null).statusCode());
        assertEquals(200, enviar("GET", "/api/v1/clientes/" + idMaria + "/deuda", tokenPepe, null).statusCode());
    }

    @Test
    @Order(4)
    @DisplayName("Los errores de la solicitud devuelven su estado HTTP (404, 405, 400) y no un error 500")
    void erroresDeSolicitudNoSon500() throws Exception {
        var pepe = token("pepe", "pepe123");

        var rutaInexistente = enviar("GET", "/api/v1/ruta-que-no-existe", pepe, null);
        assertEquals(404, rutaInexistente.statusCode(), rutaInexistente.body());
        assertEquals("RUTA_NO_ENCONTRADO", json(rutaInexistente).get("codigo").asText());

        var metodoNoPermitido = enviar("DELETE", "/api/v1/clientes", pepe, null);
        assertEquals(405, metodoNoPermitido.statusCode(), metodoNoPermitido.body());

        var jsonMalo = enviar("POST", "/api/v1/ventas", pepe, "{esto no es json");
        assertEquals(400, jsonMalo.statusCode(), jsonMalo.body());
        assertEquals("ERROR_VALIDACION", json(jsonMalo).get("codigo").asText());

        var parametroMalo = enviar("GET", "/api/v1/ventas?clienteId=abc", pepe, null);
        assertEquals(400, parametroMalo.statusCode(), parametroMalo.body());
    }

    // ---------------------------------------------------------------- tiendas y giros

    @Test
    @Order(5)
    @DisplayName("La demostración deja 5 tiendas de distintos giros, cada una con su catálogo y su vendedor")
    void cincoTiendasDeDistintosGiros() throws Exception {
        var tiendas = json(enviar("GET", "/api/v1/publico/tiendas", null, null));
        assertEquals(5, tiendas.size(), tiendas.toString());
        var giros = new java.util.TreeSet<String>();
        tiendas.forEach(t -> giros.add(t.get("giro").asText()));
        assertEquals(java.util.Set.of("Bodega", "Panadería", "Botica", "Peluquería y spa"), giros);

        for (var slug : new String[]{"tiendita-don-pepe", "bodega-dona-rosa", "panaderia-espiga-dorada",
                "botica-salud-vecinal", "peluqueria-estilo-lucero"}) {
            var catalogo = json(enviar("GET", "/api/v1/publico/tiendas/" + slug + "/productos", null, null));
            assertTrue(catalogo.size() >= 10, slug + " tiene solo " + catalogo.size() + " productos");
        }
        for (var usuario : new String[]{"marta", "luis", "lucero"}) {
            var sesion = iniciarSesion(usuario, usuario + "123");
            assertEquals("VENDEDOR", sesion.get("rol").asText());
            assertTrue(json(enviar("GET", "/api/v1/clientes", sesion.get("token").asText(), null)).size() >= 3,
                    usuario + " debe tener clientes de demostración");
        }
    }

    @Test
    @Order(6)
    @DisplayName("El administrador crea una tienda con giro; el giro es opcional y no supera 40 caracteres")
    void tiendaConGiro() throws Exception {
        var admin = token("admin", "admin123");
        String cuerpo = """
                {"nombre":"Verdulería Los Andes","slug":"%s","logo":null,"giro":%s,"ruc":null,"direccion":"Mercado 1",
                 "telefono":"900111222","tipoCambioUsd":3.75,"nombreDueno":"Ana Torres","usuarioVendedor":"%s",
                 "claveVendedor":"clave123"}""";
        var creada = enviar("POST", "/api/v1/tiendas", admin, cuerpo.formatted("verduleria-los-andes", "\"Verdulería\"", "ana"));
        assertEquals(201, creada.statusCode(), creada.body());
        assertEquals("Verdulería", json(creada).get("giro").asText());
        assertEquals("Verdulería", json(enviar("GET", "/api/v1/publico/tiendas/verduleria-los-andes", null, null)).get("giro").asText());

        var sinGiro = enviar("POST", "/api/v1/tiendas", admin, cuerpo.formatted("tienda-sin-giro", "\"   \"", "beto"));
        assertEquals(201, sinGiro.statusCode(), sinGiro.body());
        assertTrue(json(sinGiro).get("giro").isNull(), "un giro en blanco se guarda como vacío");

        var largo = "\"" + "x".repeat(41) + "\"";
        var demasiadoLargo = enviar("POST", "/api/v1/tiendas", admin, cuerpo.formatted("tienda-giro-largo", largo, "carla"));
        assertEquals(400, demasiadoLargo.statusCode(), demasiadoLargo.body());
    }

    // ---------------------------------------------------------------- recorrido completo

    @Test
    @Order(10)
    @DisplayName("Fin de mes con atraso: venta, límite, mora con compensatorio, bloqueo y pago con prelación")
    void recorridoConMora() throws Exception {
        var admin = token("admin", "admin123");
        var pepe = token("pepe", "pepe123");
        simularReloj(admin, "2027-03-10T10:00:00");
        try {

            // Producto de prueba: S/ 100.00 a crédito. Elena (límite 250, TEA 24 %, mora TEA 36 %, corte 20, pago 26) no tiene deuda.
            var producto = enviar("POST", "/api/v1/productos", pepe, """
                    {"nombre":"Producto de prueba de integración","categoria":"Pruebas","marca":"Prueba","proveedor":"Prueba",
                     "descripcion":"Producto para la prueba","unidadMedida":"und","imagen":null,
                     "precioContado":90.00,"precioCredito":100.00,"permiteCuotas":false}""");
            assertEquals(201, producto.statusCode(), producto.body());
            long productoId = json(producto).get("id").asLong();
            long elena = idCliente(pepe, DNI_ELENA);
            String venta = "{\"clienteId\":%d,\"lineas\":[{\"productoId\":%d,\"cantidad\":%s,\"modalidad\":\"FIN_DE_MES\",\"numCuotas\":null}]}";

            // 1) Venta de 2 unidades antes del corte: 16 días hasta el 26/03, interés 1.92.
            var creada = enviar("POST", "/api/v1/ventas", pepe, venta.formatted(elena, productoId, "2"));
            assertEquals(201, creada.statusCode(), creada.body());
            var obligacion = json(creada).get("obligaciones").get(0);
            assertDinero("200.00", obligacion.get("capital"), "capital");
            assertEquals(16, obligacion.get("diasGracia").asLong());
            assertEquals("2027-03-26", obligacion.get("fechaPago").asText());
            assertDinero("1.92", obligacion.get("interesCompensatorio"), "interés por los días");

            // 2) Otra unidad supera el límite de S/ 250.00 (200.00 + 100.00).
            var excedida = enviar("POST", "/api/v1/ventas", pepe, venta.formatted(elena, productoId, "1"));
            assertTrue(excedida.statusCode() >= 400 && excedida.statusCode() < 500, "status " + excedida.statusCode());
            assertTrue(excedida.body().contains("límite de crédito"), excedida.body());

            // 3) Cinco días después del vencimiento: mora 0.86 + compensatorio de atraso 0.60 sobre 201.92.
            simularReloj(admin, "2027-03-31T10:00:00");
            var deuda = json(enviar("GET", "/api/v1/clientes/" + elena + "/deuda", pepe, null));
            assertTrue(deuda.get("enMora").asBoolean());
            assertDinero("203.38", deuda.get("totalVencido"), "total vencido");
            var enMora = deuda.get("obligaciones").get(0);
            assertEquals(5, enMora.get("diasMora").asLong());
            assertDinero("0.86", enMora.get("mora"), "interés moratorio");
            assertDinero("0.60", enMora.get("interesCompensatorioAtraso"), "interés compensatorio por atraso");
            assertDinero("203.38", enMora.get("importeExigible"), "importe exigible");

            // 4) Con mora no se vende más crédito.
            var bloqueada = enviar("POST", "/api/v1/ventas", pepe, venta.formatted(elena, productoId, "1"));
            assertTrue(bloqueada.statusCode() >= 400 && bloqueada.statusCode() < 500, "status " + bloqueada.statusCode());
            assertTrue(bloqueada.body().contains("mora"), bloqueada.body());

            // 5) Pago del ciclo: prelación mora, interés (2.52 = 1.92 + 0.60) y capital.
            var pago = enviar("POST", "/api/v1/clientes/" + elena + "/pagos-ciclo", pepe, "{\"fechaPagoCiclo\":\"2027-03-26\"}");
            assertEquals(200, pago.statusCode(), pago.body());
            var resumen = json(pago);
            assertDinero("0.86", resumen.get("mora"), "mora pagada");
            assertDinero("2.52", resumen.get("interes"), "interés pagado");
            assertDinero("200.00", resumen.get("capital"), "capital pagado");
            assertDinero("203.38", resumen.get("total"), "total pagado");

            // 6) Queda al día y sin deuda.
            var despues = json(enviar("GET", "/api/v1/clientes/" + elena + "/deuda", pepe, null));
            assertFalse(despues.get("enMora").asBoolean());
            assertDinero("0.00", despues.get("capitalPendiente"), "capital pendiente");
        } finally {
            // El reloj simulado es estado compartido de la aplicación: se restaura para no afectar a otras pruebas.
            enviar("DELETE", "/api/v1/reloj", admin, null);
        }
    }
}
