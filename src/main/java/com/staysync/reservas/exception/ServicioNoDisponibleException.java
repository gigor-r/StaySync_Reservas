package com.staysync.reservas.exception;

public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String servicio) {
        super("Servicio no disponible: " + servicio + ". Intente más tarde.");
    }
}
