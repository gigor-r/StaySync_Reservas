package com.staysync.reservas.dto.response;

import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.Reserva.FuenteReserva;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Builder.Default
    private List<HuespedAdicionalResponse> huespedesAdicionales = new ArrayList<>();

    @Builder.Default
    private String horaCheckin = "15:00";

    @Builder.Default
    private String horaCheckout = "12:00";

    @Data
    @Builder
    public static class HuespedAdicionalResponse {
        private Long id;
        private String nombre;
        private String apellido;
        private String documento;
    }
}
