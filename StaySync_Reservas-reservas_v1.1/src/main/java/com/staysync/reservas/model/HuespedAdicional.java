package com.staysync.reservas.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "huespedes_adicionales")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class HuespedAdicional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(length = 50)
    private String documento;
}
