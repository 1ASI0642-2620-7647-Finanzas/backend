package com.bodega.plataforma.credito.dominio.servicios;

import com.bodega.plataforma.compartido.dominio.servicios.PotenciaDecimal;
import com.bodega.plataforma.compartido.dominio.servicios.Redondeo;
import com.bodega.plataforma.credito.dominio.modelo.objetosvalor.TasaAnual;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.bodega.plataforma.compartido.dominio.servicios.Redondeo.CONTEXTO_CALCULO;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Casos resueltos por el profesor en clase (ver TranscripcionClases/RESUMEN_CLASES_INFORMACION_UTIL.md, §6).
 * Los resultados esperados son los que él obtuvo con su calculadora, con el redondeo a 2 decimales por hito.
 */
class CalculadoraFinancieraClasesTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static void assertCerca(String esperado, BigDecimal real, String tolerancia) {
        var diferencia = bd(esperado).subtract(real).abs();
        assertTrue(diferencia.compareTo(bd(tolerancia)) <= 0,
                "esperado " + esperado + " ± " + tolerancia + " pero fue " + real);
    }

    private static void assertDinero(String esperado, BigDecimal real) {
        assertEquals(0, bd(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    /** Factor (1 + TN/m)^n de una tasa nominal. */
    private static BigDecimal factorNominal(String tn, int m, int n) {
        return BigDecimal.ONE.add(bd(tn).divide(BigDecimal.valueOf(m), CONTEXTO_CALCULO)).pow(n, CONTEXTO_CALCULO);
    }

    /** Factor (1 + i')^(numerador/denominador) de una tasa efectiva. */
    private static BigDecimal factorEfectivo(String tasa, long numerador, long denominador) {
        return PotenciaDecimal.potencia(BigDecimal.ONE.add(bd(tasa)), numerador, denominador);
    }

    private static BigDecimal avanzar(BigDecimal saldo, BigDecimal factor) {
        return Redondeo.dinero(saldo.multiply(factor));
    }

    // ---------------------------------------------------------------- conversiones y valor del dinero

    @Test
    @DisplayName("Conversiones: nominal semestral con capitalización mensual y nominal quincenal a TEA")
    void conversionesNominalEfectiva() {
        assertCerca("0.0616778", factorNominal("0.03", 6, 12).subtract(BigDecimal.ONE), "0.0000001");
        assertCerca("0.0617570", factorNominal("0.06", 24, 24).subtract(BigDecimal.ONE), "0.0000001");
        // TNS 12 % con capitalización quincenal a 60 días (m = 12, n = 4)
        assertCerca("0.04060401", factorNominal("0.12", 12, 4).subtract(BigDecimal.ONE), "0.00000001");
    }

    @Test
    @DisplayName("TNA 8 % capitalizable trimestralmente: TEA 8.2432 %")
    void nominalTrimestral() {
        assertEquals(bd("0.082432160"), CalculadoraFinanciera.tasaEfectivaAnual(new TasaAnual(true, bd("8"), 90)));
    }

    @Test
    @DisplayName("Prepago: S/ 12 000 a 45 días con TEA 15 % valen S/ 11 792.18 hoy")
    void prepago() {
        assertDinero("11792.18", CalculadoraFinanciera.valorPresente(bd("12000"), bd("0.15"), 45));
    }

    @Test
    @DisplayName("Tasa implícita: préstamo de 5 000 con regalo de 450 a 180 días = TEA 18.81 %")
    void tasaImplicitaDeLaParrilla() {
        assertEquals(bd("0.188100000"), CalculadoraFinanciera.tasaEfectivaAnualImplicita(bd("5450"), bd("5000"), 180));
    }

    @Test
    @DisplayName("Capital que rinde S/ 150 en 60 días con TE trimestral 2 % = S/ 11 287.29")
    void capitalQueGeneraUnInteres() {
        var rendimiento = factorEfectivo("0.02", 60, 90).subtract(BigDecimal.ONE);
        assertCerca("11287.29", bd("150").divide(rendimiento, CONTEXTO_CALCULO), "0.02");
    }

    @Test
    @DisplayName("TEA 25 % a 75 días: efectiva 4.7586 % y descontada 4.5424 %")
    void efectivaYDescontadaA75Dias() {
        var efectiva = CalculadoraFinanciera.tasaPorDias(bd("0.25"), 75);
        assertCerca("0.0475857", efectiva, "0.0000001");
        assertCerca("0.0454242", CalculadoraFinanciera.tasaDescontada(efectiva), "0.0000001");
    }

    @Test
    @DisplayName("Descontada de 120 días 8 % a TEA: TEC 8.6957 % y TEA 28.42 %")
    void descontadaATea() {
        var tec = CalculadoraFinanciera.tasaEfectivaDesdeDescontada(bd("0.08"));
        assertCerca("0.0869565", tec, "0.0000001");
        var tea = PotenciaDecimal.potencia(BigDecimal.ONE.add(tec), 360, 120).subtract(BigDecimal.ONE);
        assertCerca("0.2842114", tea, "0.0000001");
    }

    @Test
    @DisplayName("Descontada de 45 días 4 % → descontada de 90 días 7.84 % (no es el doble)")
    void descontadaOtroPlazo() {
        var te45 = CalculadoraFinanciera.tasaEfectivaDesdeDescontada(bd("0.04"));
        var te90 = PotenciaDecimal.potencia(BigDecimal.ONE.add(te45), 90, 45).subtract(BigDecimal.ONE);
        assertCerca("0.0850694", te90, "0.0000001");
        assertCerca("0.0784000", CalculadoraFinanciera.tasaDescontada(te90), "0.0000001");
    }

    @Test
    @DisplayName("Descuento a 60 días de VN 1 000 con TEM 3.8 %: neto S/ 928.12")
    void valorNetoDeUnTituloValor() {
        var tea = factorEfectivo("0.038", 12, 1).subtract(BigDecimal.ONE);
        assertDinero("928.12", CalculadoraFinanciera.valorPresente(bd("1000"), tea, 60));
    }

    @Test
    @DisplayName("Ahorro AFP: 300 mensuales durante 36 meses con TEA 12 % = S/ 12 802.30")
    void anualidadDeAhorro() {
        var i = CalculadoraFinanciera.tasaMensual(bd("0.12"));
        assertCerca("0.00948879", i, "0.00000001");
        var futuro = bd("300").multiply(BigDecimal.ONE.add(i).pow(36, CONTEXTO_CALCULO).subtract(BigDecimal.ONE))
                .divide(i, CONTEXTO_CALCULO);
        assertCerca("12802.30", futuro, "0.01");
    }

    // ---------------------------------------------------------------- flujos de fondos con cambio de tasa

    @Test
    @DisplayName("Flujo de Marta y Pepe: tasas nominal y efectiva mezcladas, cierre S/ 12 575.00")
    void flujoDeMartaYPepe() {
        var mes5 = avanzar(bd("4000"), factorNominal("0.18", 12, 5)).subtract(bd("2343"));
        assertDinero("1966.14", mes5);
        var mes7 = avanzar(mes5, factorNominal("0.18", 12, 2)).add(bd("2990"));
        assertDinero("5015.57", mes7);
        var mes9 = avanzar(mes7, factorNominal("0.18", 12, 2));
        assertDinero("5167.17", mes9);
        var mes15 = avanzar(mes9, factorEfectivo("0.054", 180, 90)).add(bd("2950"));
        assertDinero("8690.29", mes15);
        var mes18 = avanzar(mes15, factorEfectivo("0.054", 90, 90)).add(bd("2250"));
        assertDinero("11409.57", mes18);
        var mes21 = avanzar(mes18, factorEfectivo("0.054", 90, 90));
        assertDinero("12025.69", mes21);
        var cierre = avanzar(mes21, factorNominal("0.03", 2, 3));
        assertDinero("12575.00", cierre);
    }

    @Test
    @DisplayName("Flujo con 6 hitos y fechas (2006-2009): cierre S/ 77 780.03")
    void flujoConSeisHitos() {
        var h1 = avanzar(bd("15900"), factorNominal("0.01", 30, 313)).add(bd("19300"));
        assertDinero("36948.22", h1);
        var h2 = avanzar(h1, factorNominal("0.01", 30, 166));
        assertDinero("39049.95", h2);
        var h3 = avanzar(h2, factorEfectivo("0.15", 44, 360)).add(bd("12800"));
        assertDinero("52522.73", h3);
        var h4 = avanzar(h3, factorEfectivo("0.15", 191, 360)).add(bd("17200"));
        assertDinero("73765.40", h4);
        var h5 = avanzar(h4, factorEfectivo("0.15", 85, 360));
        assertDinero("76240.22", h5);
        var cierre = avanzar(h5, factorNominal("0.16", 360, 45));
        assertDinero("77780.03", cierre);
    }

    // ---------------------------------------------------------------- descuento de títulos valores y TCEA

    @Test
    @DisplayName("Letra de S/ 3 500 a 87 días con TEA 28 %: recibido 3 254.80, TCEA 39.91 %")
    void letraDeCambio() {
        var te87 = CalculadoraFinanciera.tasaPorDias(bd("0.28"), 87);
        assertCerca("0.0614733", te87, "0.0000001");
        var d = CalculadoraFinanciera.tasaDescontada(te87);
        var descuento = Redondeo.dinero(bd("3500").multiply(d));
        assertDinero("202.70", descuento);
        var neto = bd("3500").subtract(descuento);
        assertDinero("3297.30", neto);
        var recibido = neto.subtract(Redondeo.dinero(bd("3500").multiply(bd("0.005")))).subtract(bd("25"));
        assertDinero("3254.80", recibido);
        var entregado = bd("3500").add(bd("10")).add(bd("20"));
        assertCerca("0.3991", CalculadoraFinanciera.tasaEfectivaAnualImplicita(entregado, recibido, 87), "0.0001");
    }

    @Test
    @DisplayName("Letra con 4 días de mora: compensatorio 9.61, entregado 3 729.88, TCEA 71.43 %")
    void letraConMora() {
        assertDinero("9.61", CalculadoraFinanciera.interes(bd("3500"), bd("0.28"), 4));
        var recibido = bd("3254.80");
        var entregado = bd("3530").add(bd("20.27")).add(bd("9.61")).add(bd("40")).add(bd("50")).add(bd("80"));
        assertDinero("3729.88", entregado);
        assertCerca("0.7143", CalculadoraFinanciera.tasaEfectivaAnualImplicita(entregado, recibido, 91), "0.0001");
    }

    @Test
    @DisplayName("Pagaré de S/ 250 000 a 150 días con retención y seguro: recibido 213 097.03, TCEA 29.78 %")
    void pagare() {
        var te150 = CalculadoraFinanciera.tasaPorDias(bd("0.27"), 150);
        assertCerca("0.1047183", te150, "0.0000001");
        var d = CalculadoraFinanciera.tasaDescontada(te150);
        assertCerca("0.0947919", d, "0.0000001");
        var descuento = Redondeo.dinero(bd("250000").multiply(d));
        assertDinero("23697.97", descuento);
        var neto = bd("250000").subtract(descuento);
        assertDinero("226302.03", neto);
        var recibido = neto.subtract(bd("200")).subtract(bd("5"))
                .subtract(Redondeo.dinero(bd("250000").multiply(bd("0.002"))))
                .subtract(Redondeo.dinero(bd("250000").multiply(bd("0.05"))));
        assertDinero("213097.03", recibido);
        var entregado = bd("250000").add(bd("15")).add(bd("30")).subtract(bd("12500"));
        assertDinero("237545.00", entregado);
        assertCerca("0.2977899", CalculadoraFinanciera.tasaEfectivaAnualImplicita(entregado, recibido, 150), "0.000001");
    }

    @Test
    @DisplayName("Pagaré con 5 días de mora: moratorio 2 265.11, compensatorio 831.30, TCEA 32.71 %")
    void pagareConMora() {
        var teaMora = CalculadoraFinanciera.tasaEfectivaAnual(new TasaAnual(true, bd("65"), 1));
        assertDinero("2265.11", CalculadoraFinanciera.interesMora(bd("250000"), teaMora, 5));
        assertDinero("831.30", CalculadoraFinanciera.interes(bd("250000"), bd("0.27"), 5));
        assertDinero("831.30", CalculadoraFinanciera.interesCompensatorioAtraso(bd("250000"), bd("0.27"), 5));
        assertDinero("0.00", CalculadoraFinanciera.interesCompensatorioAtraso(bd("250000"), bd("0.27"), 0));
        var entregado = bd("237545").add(bd("2265.11")).add(bd("831.30")).add(bd("70"));
        assertDinero("240711.41", entregado);
        assertCerca("0.3271", CalculadoraFinanciera.tasaEfectivaAnualImplicita(entregado, bd("213097.03"), 155), "0.0001");
    }

    @Test
    @DisplayName("Una mora fija de S/ 500 por un día sobre S/ 2 000 equivale a una TEA absurda (≈ 7.7 × 10^34)")
    void moraFijaEsUnaTasaAbsurda() {
        var tea = CalculadoraFinanciera.tasaEfectivaAnualImplicita(bd("2500"), bd("2000"), 1);
        assertTrue(tea.compareTo(bd("7e34")) > 0 && tea.compareTo(bd("8e34")) < 0, "TEA fue " + tea);
    }

    // ---------------------------------------------------------------- precisión y validaciones

    @Test
    @DisplayName("Potencia decimal coincide con Math.pow y es consistente")
    void potenciaDecimal() {
        assertCerca("1.4142135623730950", PotenciaDecimal.potencia(bd("2"), 1, 2), "0.0000000000000001");
        assertCerca(String.valueOf(Math.pow(1.24, 11.0 / 360)), PotenciaDecimal.potencia(bd("1.24"), 11, 360), "0.000000000000001");
        assertCerca(String.valueOf(Math.pow(13.5, 90.0 / 360)), PotenciaDecimal.potencia(bd("13.5"), 90, 360), "0.000000000000001");
        assertCerca(String.valueOf(Math.pow(1.3, -7.0 / 3)), PotenciaDecimal.potencia(bd("1.3"), -7, 3), "0.000000000000001");
        assertEquals(0, BigDecimal.ONE.compareTo(PotenciaDecimal.potencia(bd("1.9"), 0, 360)));
        assertThrows(IllegalArgumentException.class, () -> PotenciaDecimal.potencia(bd("-1"), 1, 2));
        assertThrows(IllegalArgumentException.class, () -> PotenciaDecimal.potencia(bd("2"), 1, 0));
    }

    @Test
    @DisplayName("Tasa por plazo: 360 días devuelve la TEA y 30 días coincide con la tasa mensual")
    void coherenciaDeTasas() {
        var tea = bd("0.2400");
        assertEquals(0, CalculadoraFinanciera.tasaPorDias(tea, 360).compareTo(tea));
        assertEquals(CalculadoraFinanciera.tasaMensual(tea), CalculadoraFinanciera.tasaPorDias(tea, 30));
        var tea3 = CalculadoraFinanciera.tasaPorDias(bd("0.24"), 360 * 3);
        assertCerca("0.9066240", tea3, "0.0000001");
    }

    @Test
    @DisplayName("La capitalización de una tasa nominal debe dividir al año de 360 días (no hay capitalización semanal)")
    void capitalizacionValida() {
        for (int dias : new int[]{1, 15, 30, 60, 90, 120, 180, 360})
            assertDoesNotThrow(() -> new TasaAnual(true, bd("24"), dias), "capitalización de " + dias + " días");
        for (int dias : new int[]{0, -30, 7, 365, 25})
            assertThrows(IllegalArgumentException.class, () -> new TasaAnual(true, bd("24"), dias), "capitalización de " + dias + " días");
        assertDoesNotThrow(() -> new TasaAnual(false, bd("24"), 0), "en una efectiva se ignora");
    }
}
