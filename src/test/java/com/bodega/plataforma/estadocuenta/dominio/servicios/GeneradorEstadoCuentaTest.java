package com.bodega.plataforma.estadocuenta.dominio.servicios;

import com.bodega.plataforma.credito.dominio.modelo.agregados.Obligacion;
import com.bodega.plataforma.credito.dominio.servicios.CalculadoraFinanciera;
import com.bodega.plataforma.estadocuenta.dominio.modelo.objetosvalor.TipoLinea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Estado de cuenta con mora según el libro (F34 a F37): además del interés moratorio aparece, en ítem aparte,
 * el interés compensatorio por los días de atraso. Datos del juego 1 del informe (compra de S/ 500.00 el 15/09,
 * pago el 26/09, TEA compensatoria 24 %, TEA mora 36 %).
 */
class GeneradorEstadoCuentaTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static Obligacion compraDelJuego1() {
        var compra = LocalDateTime.of(2026, 9, 15, 10, 0);
        var ciclo = CalculadoraFinanciera.calcularCiclo(compra, 20, LocalTime.of(23, 59), 26);
        return Obligacion.finDeMes(1L, 1L, "PEN", bd("500.00"), compra, ciclo, bd("0.24"), bd("0.36"));
    }

    @Test
    @DisplayName("Sin atraso: compra e interés por días; no hay ítems de atraso")
    void sinAtraso() {
        var lineas = GeneradorEstadoCuenta.generarLineas(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 26),
                List.of(compraDelJuego1()), List.of(), LocalDate.of(2026, 9, 26));
        assertEquals(List.of(TipoLinea.COMPRA, TipoLinea.INTERES_COMPENSATORIO),
                lineas.stream().map(l -> l.getTipo()).toList());
        assertEquals(bd("503.30"), GeneradorEstadoCuenta.total(lineas));
    }

    @Test
    @DisplayName("5 días de atraso: mora 2.15 y compensatorio de atraso 1.51 en ítems aparte; total 506.96")
    void conAtraso() {
        var lineas = GeneradorEstadoCuenta.generarLineas(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 26),
                List.of(compraDelJuego1()), List.of(), LocalDate.of(2026, 10, 1));
        assertEquals(4, lineas.size());
        var porTipo = lineas.stream().collect(java.util.stream.Collectors.groupingBy(l -> l.getTipo()));
        assertEquals(2, porTipo.get(TipoLinea.INTERES_COMPENSATORIO).size(), "por días de gracia y por atraso");
        assertEquals(bd("2.15"), porTipo.get(TipoLinea.MORA).get(0).getMonto());
        assertEquals(bd("1.51"), porTipo.get(TipoLinea.INTERES_COMPENSATORIO).stream()
                .filter(l -> l.getDescripcion().startsWith("Intereses compensatorios por atraso"))
                .findFirst().orElseThrow().getMonto());
        assertEquals(bd("506.96"), GeneradorEstadoCuenta.total(lineas));
    }
}
