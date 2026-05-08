package com.staysync.reservas.exception;

public class ReservaNotFoundException extends RuntimeException {
    public ReservaNotFoundException(Long id) { super("Reserva no encontrada con ID: " + id); }
    public ReservaNotFoundException(String codigo) { super("Reserva no encontrada con código: " + codigo); }
}
