package com.staysync.reservas.dto.response;

import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.Reserva.FuenteReserva;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ReservaResponse {
    private Long id;
    private String codigo;
    private Long usuarioId;
    private Long habitacionId;
    private LocalDate fechaEntrada;
    private LocalDate fechaSalida;
    private Integer numHuespedes;
    private EstadoReserva estado;
    private BigDecimal precioTotal;
    private String notas;
    private FuenteReserva fuente;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
