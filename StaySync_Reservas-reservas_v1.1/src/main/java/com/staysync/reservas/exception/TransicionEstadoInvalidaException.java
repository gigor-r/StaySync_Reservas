package com.staysync.reservas.exception;

public class TransicionEstadoInvalidaException extends RuntimeException {
    public TransicionEstadoInvalidaException(String estadoActual, String estadoNuevo) {
        super("Transición de estado inválida: " + estadoActual + " → " + estadoNuevo);
    }
}
