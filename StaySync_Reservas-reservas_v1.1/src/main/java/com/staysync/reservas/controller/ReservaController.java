package com.staysync.reservas.controller;

import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Gestión completa del ciclo de vida de reservas hoteleras")
public class ReservaController {

    private final ReservaService reservaService;

    @GetMapping
    @Operation(summary = "Listar reservas con paginación y filtros")
    public ResponseEntity<Page<ReservaResponse>> listar(
            @RequestParam(defaultValue = "0")           int page,
            @RequestParam(defaultValue = "10")          int size,
            @RequestParam(defaultValue = "createdAt")   String sort) {
        return ResponseEntity.ok(reservaService.listar(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener reserva por ID")
    public ResponseEntity<ReservaResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    @Operation(summary = "Buscar reserva por código único")
    public ResponseEntity<ReservaResponse> obtenerPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(reservaService.obtenerPorCodigo(codigo));
    }

    @GetMapping("/usuario/{usuarioId}")
    @Operation(summary = "Listar reservas de un usuario")
    public ResponseEntity<Page<ReservaResponse>> listarPorUsuario(
            @PathVariable Long usuarioId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reservaService.listarPorUsuario(
                usuarioId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/habitacion/{habitacionId}/conflictos")
    @Operation(summary = "Verificar si existen conflictos de fechas (uso interno)")
    public ResponseEntity<Map<String, Boolean>> verificarConflicto(
            @PathVariable Long habitacionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEntrada,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaSalida) {
        boolean conflicto = reservaService.tieneConflicto(habitacionId, fechaEntrada, fechaSalida);
        return ResponseEntity.ok(Map.of("tieneConflicto", conflicto));
    }

    @PostMapping
    @Operation(summary = "Crear nueva reserva")
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(request));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar estado de una reserva")
    public ResponseEntity<ReservaResponse> cambiarEstado(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body) {
        EstadoReserva estado = EstadoReserva.valueOf(body.get("estado"));
        return ResponseEntity.ok(reservaService.cambiarEstado(id, estado));
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar reserva")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        reservaService.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar reserva (uso interno/admin)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        reservaService.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
