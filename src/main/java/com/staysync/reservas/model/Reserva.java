package com.staysync.reservas.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservas", indexes = {
        @Index(name = "idx_usuario_id",    columnList = "usuario_id"),
        @Index(name = "idx_habitacion_id", columnList = "habitacion_id"),
        @Index(name = "idx_estado",        columnList = "estado"),
        @Index(name = "idx_fechas",        columnList = "fecha_entrada,fecha_salida"),
        @Index(name = "idx_codigo",        columnList = "codigo")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "habitacion_id", nullable = false)
    private Long habitacionId;

    @Column(name = "fecha_entrada", nullable = false)
    private LocalDate fechaEntrada;

    @Column(name = "fecha_salida", nullable = false)
    private LocalDate fechaSalida;

    @Column(name = "num_huespedes", nullable = false)
    @Builder.Default
    private Integer numHuespedes = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoReserva estado = EstadoReserva.PENDIENTE;

    @Column(name = "precio_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioTotal;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private FuenteReserva fuente = FuenteReserva.DIRECTO;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HuespedAdicional> huespedesAdicionales = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum EstadoReserva {
        PENDIENTE, CONFIRMADA, CHECKIN, CHECKOUT, CANCELADA, NO_SHOW
    }

    public enum FuenteReserva {
        DIRECTO, OTA, TELEFONO
    }
}
