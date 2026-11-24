package com.bodega.plataforma.credito.dominio.servicios;

import com.bodega.plataforma.compartido.dominio.servicios.PotenciaDecimal;
import com.bodega.plataforma.compartido.dominio.servicios.Redondeo;
import com.bodega.plataforma.credito.dominio.modelo.entidades.Cuota;
import com.bodega.plataforma.credito.dominio.modelo.objetosvalor.Ciclo;
import com.bodega.plataforma.credito.dominio.modelo.objetosvalor.EstadoDeuda;
import com.bodega.plataforma.credito.dominio.modelo.objetosvalor.TasaAnual;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.bodega.plataforma.compartido.dominio.servicios.Redondeo.CONTEXTO_CALCULO;

/**
 * Servicio de dominio con las fórmulas financieras del sistema (Anexo A del enunciado):
 * base de 360 días, mes comercial de 30 días, dinero con 2 decimales y tasas con 9 decimales internos.
 * Es una clase pura (sin Spring ni base de datos) para poder probarla con datos de prueba.
 */
public final class CalculadoraFinanciera {

    public static final int BASE_ANUAL = 360;
    public static final int DIAS_MES = 30;

    private CalculadoraFinanciera() {
    }

    /**
     * Convierte la tasa pactada a tasa efectiva anual (TEA), en forma decimal (0.24 = 24 %).
     * Nominal con capitalización cada k días: TEA = (1 + TNA / (360/k))^(360/k) - 1.
     */
    public static BigDecimal tasaEfectivaAnual(TasaAnual tasa) {
        var anual = tasa.porcentaje().divide(BigDecimal.valueOf(100), CONTEXTO_CALCULO);
        if (!tasa.nominal()) return Redondeo.tasa(anual);
        int periodos = BASE_ANUAL / tasa.capitalizacionDias();
        var base = BigDecimal.ONE.add(anual.divide(BigDecimal.valueOf(periodos), CONTEXTO_CALCULO));
        return Redondeo.tasa(base.pow(periodos, CONTEXTO_CALCULO).subtract(BigDecimal.ONE));
    }

    /** Tasa efectiva para un plazo de {@code dias}: i(d) = (1 + TEA)^(d/360) - 1. */
    public static BigDecimal tasaPorDias(BigDecimal tea, long dias) {
        if (dias <= 0) return BigDecimal.ZERO.setScale(Redondeo.DECIMALES_TASA);
        var factor = PotenciaDecimal.potencia(BigDecimal.ONE.add(tea), dias, BASE_ANUAL);
        return Redondeo.tasa(factor.subtract(BigDecimal.ONE));
    }

    /** Valor presente de un monto futuro con TEA: C = S / (1 + TEA)^(dias/360) (prepago de una deuda). */
    public static BigDecimal valorPresente(BigDecimal montoFuturo, BigDecimal tea, long dias) {
        var factor = PotenciaDecimal.potencia(BigDecimal.ONE.add(tea), dias, BASE_ANUAL);
        return Redondeo.dinero(montoFuturo.divide(factor, CONTEXTO_CALCULO));
    }

    /** Tasa descontada de un plazo a partir de la efectiva del mismo plazo: d = i' / (1 + i'). */
    public static BigDecimal tasaDescontada(BigDecimal tasaEfectivaDelPlazo) {
        return Redondeo.tasa(tasaEfectivaDelPlazo.divide(BigDecimal.ONE.add(tasaEfectivaDelPlazo), CONTEXTO_CALCULO));
    }

    /** Tasa efectiva de un plazo a partir de la descontada del mismo plazo: i' = d / (1 - d). */
    public static BigDecimal tasaEfectivaDesdeDescontada(BigDecimal tasaDescontada) {
        return Redondeo.tasa(tasaDescontada.divide(BigDecimal.ONE.subtract(tasaDescontada), CONTEXTO_CALCULO));
    }

    /**
     * Tasa efectiva anual implícita (o de costo efectivo, TCEA) de una operación que entrega {@code recibido}
     * y devuelve {@code entregado} tras {@code dias} días: (entregado / recibido)^(360/dias) - 1.
     */
    public static BigDecimal tasaEfectivaAnualImplicita(BigDecimal entregado, BigDecimal recibido, long dias) {
        if (dias <= 0) throw new IllegalArgumentException("El plazo debe ser positivo");
        var razon = entregado.divide(recibido, CONTEXTO_CALCULO);
        return Redondeo.tasa(PotenciaDecimal.potencia(razon, BASE_ANUAL, dias).subtract(BigDecimal.ONE));
    }

    /** Tasa efectiva mensual de 30 días: i_m = (1 + TEA)^(30/360) - 1. */
    public static BigDecimal tasaMensual(BigDecimal tea) {
        return tasaPorDias(tea, DIAS_MES);
    }

    /** Interés compensatorio de un capital durante {@code dias} días. */
    public static BigDecimal interes(BigDecimal capital, BigDecimal tea, long dias) {
        return Redondeo.dinero(capital.multiply(tasaPorDias(tea, dias)));
    }

    /** Interés moratorio (F36/F37 del libro) sobre el monto vencido durante {@code diasMora} días (cero si no hay mora). */
    public static BigDecimal interesMora(BigDecimal montoVencido, BigDecimal teaMora, long diasMora) {
        if (diasMora <= 0) return Redondeo.dinero(BigDecimal.ZERO);
        return interes(montoVencido, teaMora, diasMora);
    }

    /**
     * Interés compensatorio por los días transcurridos después del vencimiento (F34/F35 del libro): el monto
     * vencido sigue generando interés a la tasa compensatoria pactada y se suma al moratorio (cero si no hay atraso).
     */
    public static BigDecimal interesCompensatorioAtraso(BigDecimal montoVencido, BigDecimal teaCompensatoria,
                                                        long diasAtraso) {
        if (diasAtraso <= 0) return Redondeo.dinero(BigDecimal.ZERO);
        return interes(montoVencido, teaCompensatoria, diasAtraso);
    }

    /** Capitaliza el periodo de gracia: P' = P · (1 + TEA)^(dias/360). */
    public static BigDecimal capitalizarGracia(BigDecimal capital, BigDecimal tea, long diasGracia) {
        return Redondeo.dinero(capital.add(interes(capital, tea, diasGracia)));
    }

    /** Cuota constante del método francés vencido: C = P · i · (1+i)^n / ((1+i)^n - 1). */
    public static BigDecimal cuotaFrancesa(BigDecimal principal, BigDecimal tasaMensual, int numCuotas) {
        var factor = BigDecimal.ONE.add(tasaMensual).pow(numCuotas, CONTEXTO_CALCULO);
        var cuota = principal.multiply(tasaMensual).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), CONTEXTO_CALCULO);
        return Redondeo.dinero(cuota);
    }

    /**
     * Cronograma de cuotas. La primera cuota vence un mes después de la fecha de pago del ciclo.
     * La última cuota absorbe el residuo del redondeo.
     */
    public static List<Cuota> cronogramaFrances(BigDecimal principalCapitalizado, BigDecimal tea, int numCuotas,
                                                LocalDate fechaPagoCiclo) {
        var tasaMensual = tasaMensual(tea);
        var cuotaFija = cuotaFrancesa(principalCapitalizado, tasaMensual, numCuotas);
        var cuotas = new ArrayList<Cuota>();
        var saldo = principalCapitalizado;
        for (int k = 1; k <= numCuotas; k++) {
            var interes = Redondeo.dinero(saldo.multiply(tasaMensual));
            var amortizacion = k < numCuotas ? cuotaFija.subtract(interes) : saldo;
            var cuota = amortizacion.add(interes);
            saldo = saldo.subtract(amortizacion);
            cuotas.add(new Cuota(k, fechaPagoCiclo.plusMonths(k), cuota, interes, amortizacion, saldo,
                    EstadoDeuda.PENDIENTE));
        }
        return cuotas;
    }

    /**
     * Determina el ciclo de una compra. Una compra realizada hasta la fecha y hora de corte del mes pertenece
     * al ciclo de ese mes; una posterior, al ciclo del mes siguiente. La fecha de pago es el día de pago del
     * mes del corte si es posterior al día de corte; de lo contrario, el del mes siguiente.
     */
    public static Ciclo calcularCiclo(LocalDateTime fechaHoraCompra, int diaCorte, LocalTime horaCorte, int diaPago) {
        var corteDelMes = fechaHoraCompra.toLocalDate().withDayOfMonth(diaCorte);
        var fechaCorte = fechaHoraCompra.isAfter(corteDelMes.atTime(horaCorte)) ? corteDelMes.plusMonths(1) : corteDelMes;
        var fechaPago = diaPago > diaCorte
                ? fechaCorte.withDayOfMonth(diaPago)
                : fechaCorte.plusMonths(1).withDayOfMonth(diaPago);
        return new Ciclo(fechaCorte, fechaPago);
    }

    /** Días calendario entre dos fechas (0 si la segunda no es posterior). */
    public static long dias(LocalDate desde, LocalDate hasta) {
        return Math.max(0, ChronoUnit.DAYS.between(desde, hasta));
    }
}
