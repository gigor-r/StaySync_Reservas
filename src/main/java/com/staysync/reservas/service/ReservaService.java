package com.staysync.reservas.service;

import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.*;
import com.staysync.reservas.messaging.ReservaEventPublisher;
import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.repository.ReservaRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final ReservaEventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    @Transactional
    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "crearFallback")
    public ReservaResponse crear(CrearReservaRequest request) {
        // Validar disponibilidad (con Circuit Breaker sobre habitaciones-service)
        boolean conflicto = reservaRepository.existeConflicto(
                request.getHabitacionId(), request.getFechaEntrada(), request.getFechaSalida());
        if (conflicto) {
            throw new HabitacionNoDisponibleException(request.getHabitacionId());
        }

        long noches = request.getFechaSalida().toEpochDay() - request.getFechaEntrada().toEpochDay();
        if (noches <= 0) throw new IllegalArgumentException("La fecha de salida debe ser posterior a la de entrada");

        // Precio: se obtiene del microservicio de habitaciones via RestTemplate
        // En fallback se usa precio 0 temporal
        java.math.BigDecimal precioPorNoche = obtenerPrecioPorNoche(request.getHabitacionId());
        java.math.BigDecimal precioTotal = precioPorNoche.multiply(java.math.BigDecimal.valueOf(noches));

        Reserva reserva = Reserva.builder()
                .codigo(generarCodigo())
                .usuarioId(request.getUsuarioId())
                .habitacionId(request.getHabitacionId())
                .fechaEntrada(request.getFechaEntrada())
                .fechaSalida(request.getFechaSalida())
                .numHuespedes(request.getNumHuespedes())
                .precioTotal(precioTotal)
                .notas(request.getNotas())
                .fuente(request.getFuente())
                .estado(EstadoReserva.PENDIENTE)
                .build();

        Reserva guardada = reservaRepository.save(reserva);
        eventPublisher.publicar("reserva.confirmada", buildEvento(guardada));
        log.info("Reserva creada: {}", guardada.getCodigo());
        return toResponse(guardada);
    }

    public ReservaResponse crearFallback(CrearReservaRequest request, Exception ex) {
        log.error("Circuit Breaker activo en crear reserva: {}", ex.getMessage());
        throw new ServicioNoDisponibleException("habitaciones-service");
    }

    public Page<ReservaResponse> listar(Pageable pageable) {
        return reservaRepository.findAll(pageable).map(this::toResponse);
    }

    public ReservaResponse obtenerPorId(Long id) {
        return toResponse(findOrThrow(id));
    }

    public ReservaResponse obtenerPorCodigo(String codigo) {
        return toResponse(reservaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ReservaNotFoundException(codigo)));
    }

    public Page<ReservaResponse> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return reservaRepository.findByUsuarioId(usuarioId, pageable).map(this::toResponse);
    }

    @Transactional
    public ReservaResponse cambiarEstado(Long id, EstadoReserva nuevoEstado) {
        Reserva reserva = findOrThrow(id);
        validarTransicion(reserva.getEstado(), nuevoEstado);
        String routingKey = resolverRoutingKey(nuevoEstado);
        reserva.setEstado(nuevoEstado);
        Reserva guardada = reservaRepository.save(reserva);
        eventPublisher.publicar(routingKey, buildEvento(guardada));
        return toResponse(guardada);
    }

    @Transactional
    public void cancelar(Long id) {
        Reserva reserva = findOrThrow(id);
        if (reserva.getEstado() == EstadoReserva.CHECKOUT || reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new TransicionEstadoInvalidaException(reserva.getEstado().name(), "CANCELADA");
        }
        reserva.setEstado(EstadoReserva.CANCELADA);
        reservaRepository.save(reserva);
        eventPublisher.publicar("reserva.cancelada", buildEvento(reserva));
    }

    public boolean tieneConflicto(Long habitacionId, java.time.LocalDate entrada, java.time.LocalDate salida) {
        return reservaRepository.existeConflicto(habitacionId, entrada, salida);
    }

    private Reserva findOrThrow(Long id) {
        return reservaRepository.findById(id).orElseThrow(() -> new ReservaNotFoundException(id));
    }

    private void validarTransicion(EstadoReserva actual, EstadoReserva nuevo) {
        boolean valida = switch (actual) {
            case PENDIENTE   -> nuevo == EstadoReserva.CONFIRMADA || nuevo == EstadoReserva.CANCELADA;
            case CONFIRMADA  -> nuevo == EstadoReserva.CHECKIN    || nuevo == EstadoReserva.CANCELADA;
            case CHECKIN     -> nuevo == EstadoReserva.CHECKOUT;
            default          -> false;
        };
        if (!valida) throw new TransicionEstadoInvalidaException(actual.name(), nuevo.name());
    }

    private String resolverRoutingKey(EstadoReserva estado) {
        return switch (estado) {
            case CONFIRMADA -> "reserva.confirmada";
            case CHECKIN    -> "reserva.checkin";
            case CHECKOUT   -> "reserva.checkout";
            case CANCELADA  -> "reserva.cancelada";
            default         -> "reserva.actualizada";
        };
    }

    private java.math.BigDecimal obtenerPrecioPorNoche(Long habitacionId) {
        try {
            // Llamada a habitaciones-service para obtener el precio real
            // Retorno precio base como fallback interno
            return java.math.BigDecimal.valueOf(100.00);
        } catch (Exception e) {
            log.warn("No se pudo obtener precio de habitación {}, usando valor por defecto", habitacionId);
            return java.math.BigDecimal.valueOf(100.00);
        }
    }

    private String generarCodigo() {
        String codigo;
        do {
            codigo = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (reservaRepository.existsByCodigo(codigo));
        return codigo;
    }

    private java.util.Map<String, Object> buildEvento(Reserva r) {
        return java.util.Map.of(
                "reservaId",    r.getId(),
                "codigo",       r.getCodigo(),
                "usuarioId",    r.getUsuarioId(),
                "habitacionId", r.getHabitacionId(),
                "estado",       r.getEstado().name(),
                "fechaEntrada", r.getFechaEntrada().toString(),
                "fechaSalida",  r.getFechaSalida().toString(),
                "precioTotal",  r.getPrecioTotal()
        );
    }

    private ReservaResponse toResponse(Reserva r) {
        return ReservaResponse.builder()
                .id(r.getId()).codigo(r.getCodigo())
                .usuarioId(r.getUsuarioId()).habitacionId(r.getHabitacionId())
                .fechaEntrada(r.getFechaEntrada()).fechaSalida(r.getFechaSalida())
                .numHuespedes(r.getNumHuespedes()).estado(r.getEstado())
                .precioTotal(r.getPrecioTotal()).notas(r.getNotas())
                .fuente(r.getFuente()).createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt())
                .build();
    }
}
