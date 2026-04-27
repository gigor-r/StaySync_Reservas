package com.staysync.reservas.exception;

public class HabitacionNoDisponibleException extends RuntimeException {
    public HabitacionNoDisponibleException(Long habitacionId) {
        super("La habitación " + habitacionId + " no está disponible para las fechas solicitadas");
    }
}
