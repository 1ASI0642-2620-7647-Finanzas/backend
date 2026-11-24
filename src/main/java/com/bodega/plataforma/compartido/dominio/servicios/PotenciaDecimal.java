package com.bodega.plataforma.compartido.dominio.servicios;

import java.math.BigDecimal;
import java.math.BigInteger;

import static com.bodega.plataforma.compartido.dominio.servicios.Redondeo.CONTEXTO_CALCULO;

/**
 * Potencias con exponente fraccionario en aritmética decimal (sin pasar por {@code double}).
 * Calcula {@code base^(numerador/denominador)} como la raíz de orden {@code denominador} (método de Newton)
 * elevada a {@code numerador}, con la precisión de {@link Redondeo#CONTEXTO_CALCULO}.
 */
public final class PotenciaDecimal {

    private static final BigDecimal TOLERANCIA = new BigDecimal("1e-35");
    private static final int MAXIMO_ITERACIONES = 60;

    private PotenciaDecimal() {
    }

    /** {@code base^(numerador/denominador)}; la base debe ser positiva y el denominador mayor que cero. */
    public static BigDecimal potencia(BigDecimal base, long numerador, long denominador) {
        if (base.signum() <= 0) throw new IllegalArgumentException("La base debe ser positiva");
        if (denominador <= 0) throw new IllegalArgumentException("El denominador debe ser mayor que cero");
        if (numerador == 0) return BigDecimal.ONE;
        if (base.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ONE;

        long mcd = BigInteger.valueOf(Math.abs(numerador)).gcd(BigInteger.valueOf(denominador)).longValue();
        long p = numerador / mcd;
        long q = denominador / mcd;

        var raiz = q == 1 ? base : raizEnesima(base, (int) q);
        return p > 0
                ? raiz.pow((int) p, CONTEXTO_CALCULO)
                : BigDecimal.ONE.divide(raiz.pow((int) -p, CONTEXTO_CALCULO), CONTEXTO_CALCULO);
    }

    /** Raíz n-ésima de {@code x} por Newton: y' = ((n-1)·y + x / y^(n-1)) / n. */
    static BigDecimal raizEnesima(BigDecimal x, int n) {
        var nDecimal = BigDecimal.valueOf(n);
        var nMenosUno = BigDecimal.valueOf(n - 1L);
        var y = estimacionInicial(x, n);
        for (int i = 0; i < MAXIMO_ITERACIONES; i++) {
            var siguiente = nMenosUno.multiply(y)
                    .add(x.divide(y.pow(n - 1, CONTEXTO_CALCULO), CONTEXTO_CALCULO))
                    .divide(nDecimal, CONTEXTO_CALCULO);
            var diferencia = siguiente.subtract(y).abs();
            y = siguiente;
            if (diferencia.compareTo(TOLERANCIA.multiply(y)) <= 0) break;
        }
        return y;
    }

    /** Semilla en doble precisión; para bases fuera del rango de {@code double} se usa la mantisa y el exponente. */
    private static BigDecimal estimacionInicial(BigDecimal x, int n) {
        double aprox = x.doubleValue();
        if (Double.isFinite(aprox) && aprox > 0) return new BigDecimal(Math.pow(aprox, 1.0 / n));
        double log10 = x.unscaledValue().bitLength() * Math.log10(2) - x.scale();
        return BigDecimal.TEN.pow((int) Math.floor(log10 / n));
    }
}
