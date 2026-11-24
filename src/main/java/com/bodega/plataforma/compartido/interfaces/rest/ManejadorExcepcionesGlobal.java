package com.bodega.plataforma.compartido.interfaces.rest;

import com.bodega.plataforma.compartido.aplicacion.resultado.ErrorAplicacion;
import com.bodega.plataforma.compartido.interfaces.rest.recursos.ErrorRecurso;
import com.bodega.plataforma.compartido.interfaces.rest.transformacion.ErrorRespuestaEnsamblador;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejo centralizado de excepciones para toda la API.
 */
@Slf4j
@RestControllerAdvice
public class ManejadorExcepcionesGlobal {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> manejarValidacion(MethodArgumentNotValidException ex) {
        var detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .reduce((a, b) -> a + "; " + b)
                .orElse("La solicitud no es válida");
        return ErrorRespuestaEnsamblador.aRespuestaDesdeError(ErrorAplicacion.validacion("cuerpo de la solicitud", detalle));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ErrorRespuestaEnsamblador.aRespuestaDesdeError(ErrorAplicacion.validacion("argumento", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> manejarAccesoDenegado(AccessDeniedException ex) {
        return ErrorRespuestaEnsamblador.aRespuestaDesdeError(ErrorAplicacion.prohibido("Su rol no permite esta operación"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        return ErrorRespuestaEnsamblador.aRespuestaDesdeError(ErrorAplicacion.validacion("cuerpo de la solicitud",
                "El cuerpo no es un JSON válido o tiene un valor con formato incorrecto"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> manejarTipoDeParametro(MethodArgumentTypeMismatchException ex) {
        return ErrorRespuestaEnsamblador.aRespuestaDesdeError(ErrorAplicacion.validacion("parámetro '%s'".formatted(ex.getName()),
                "El valor indicado no tiene el formato esperado"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> manejarInesperado(Exception ex) {
        // Los errores propios de Spring MVC (ruta inexistente 404, método no permitido 405, falta un parámetro 400...)
        // ya traen su estado HTTP: se respetan en lugar de convertirlos en un error 500.
        if (ex instanceof ErrorResponse respuesta && respuesta.getStatusCode().is4xxClientError()) {
            var estado = respuesta.getStatusCode();
            var detalle = respuesta.getBody().getDetail();
            var error = estado.value() == 404
                    ? new ErrorAplicacion("RUTA_NO_ENCONTRADO", "No existe la ruta solicitada", detalle)
                    : new ErrorAplicacion("SOLICITUD_INVALIDA", "La solicitud no es válida", detalle);
            return ResponseEntity.status(estado).body(new ErrorRecurso(error.codigo(), error.mensaje(), error.detalle()));
        }
        log.error("Error inesperado", ex);
        return ErrorRespuestaEnsamblador.aRespuestaDesdeError(
                ErrorAplicacion.inesperado("servidor", ex.getMessage() != null ? ex.getMessage() : "Ocurrió un error inesperado"));
    }
}
