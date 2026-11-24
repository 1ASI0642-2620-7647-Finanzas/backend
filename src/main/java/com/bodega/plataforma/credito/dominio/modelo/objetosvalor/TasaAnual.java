package com.bodega.plataforma.credito.dominio.modelo.objetosvalor;

import java.math.BigDecimal;

/**
 * Tasa pactada tal como llega desde el contexto de clientes.
 *
 * @param nominal            verdadero si es nominal anual; falso si es efectiva anual
 * @param porcentaje         tasa anual en porcentaje (24 = 24 %)
 * @param capitalizacionDias días de capitalización de una tasa nominal (1 = diaria, 30 = mensual); ignorado si es efectiva.
 *                           Debe dividir exactamente al año de 360 días (no existe la capitalización semanal).
 */
public record TasaAnual(boolean nominal, BigDecimal porcentaje, int capitalizacionDias) {

    private static final int BASE_ANUAL = 360;

    public TasaAnual {
        if (nominal && (capitalizacionDias <= 0 || BASE_ANUAL % capitalizacionDias != 0))
            throw new IllegalArgumentException("La capitalización de " + capitalizacionDias
                    + " días no es válida: debe dividir exactamente al año de " + BASE_ANUAL + " días");
    }
}
