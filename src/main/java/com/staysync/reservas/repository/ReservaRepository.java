package com.staysync.reservas.repository;

import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByCodigo(String codigo);
    Page<Reserva> findByUsuarioId(Long usuarioId, Pageable pageable);
    List<Reserva> findByHabitacionIdAndEstadoIn(Long habitacionId, List<EstadoReserva> estados);
    List<Reserva> findByEstado(EstadoReserva estado);
    List<Reserva> findByEstadoAndFechaEntrada(EstadoReserva estado, LocalDate fechaEntrada);

    @Query("""
        SELECT COUNT(r) > 0 FROM Reserva r
        WHERE r.habitacionId = :habitacionId
          AND r.estado IN ('CONFIRMADA', 'CHECKIN')
          AND r.fechaEntrada < :fechaSalida
          AND r.fechaSalida > :fechaEntrada
    """)
    boolean existeConflicto(Long habitacionId, LocalDate fechaEntrada, LocalDate fechaSalida);

    @Query("SELECT r FROM Reserva r WHERE r.estado = 'CONFIRMADA' AND r.fechaEntrada <= :hoy")
    List<Reserva> findConfirmadasParaAutoCheckin(@Param("hoy") LocalDate hoy);

    boolean existsByCodigo(String codigo);
}
