package com.staysync.reservas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Datos para crear una nueva reserva")
public class CrearReservaRequest {

    @NotNull(message = "El ID de usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El ID de habitación es obligatorio")
    private Long habitacionId;

    @NotNull(message = "La fecha de entrada es obligatoria")
    @FutureOrPresent(message = "La fecha de entrada no puede ser en el pasado")
    @Schema(example = "2025-03-01")
    private LocalDate fechaEntrada;

    @NotNull(message = "La fecha de salida es obligatoria")
    @Future(message = "La fecha de salida debe ser futura")
    @Schema(example = "2025-03-05")
    private LocalDate fechaSalida;

    @Min(value = 1, message = "Debe haber al menos 1 huésped")
    @Max(value = 10, message = "No se permiten más de 10 huéspedes por reserva")
    @Builder.Default
    private Integer numHuespedes = 1;

    @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
    private String notas;

    @lombok.Builder.Default
    private com.staysync.reservas.model.Reserva.FuenteReserva fuente =
            com.staysync.reservas.model.Reserva.FuenteReserva.DIRECTO;
}
