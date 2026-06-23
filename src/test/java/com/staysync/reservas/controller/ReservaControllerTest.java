package com.staysync.reservas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.*;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.Reserva.FuenteReserva;
import com.staysync.reservas.service.ReservaService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de capa web para ReservaController.
 * Verifica HTTP status codes, serialización JSON y mapeo de excepciones.
 * No arranca Spring completo — solo la capa web con ReservaService mockeado.
 */
@WebMvcTest(controllers = ReservaController.class)
@AutoConfigureMockMvc(addFilters = false) // deshabilita filtros de seguridad para tests de capa web
@DisplayName("ReservaController - Tests de Capa Web")
class ReservaControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ReservaService reservaService;

    private ReservaResponse reservaResponse;

    @BeforeEach
    void setUp() {
        reservaResponse = ReservaResponse.builder()
                .id(1L).codigo("RES-ABCD1234")
                .usuarioId(1L).habitacionId(1L)
                .fechaEntrada(LocalDate.now().plusDays(5))
                .fechaSalida(LocalDate.now().plusDays(7))
                .numHuespedes(2)
                .estado(EstadoReserva.PENDIENTE)
                .precioTotal(BigDecimal.valueOf(200))
                .fuente(FuenteReserva.DIRECTO)
                .huespedesAdicionales(Collections.emptyList())
                .horaCheckin("15:00")
                .horaCheckout("12:00")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/v1/reservas ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /reservas - debe retornar 201 con reserva creada")
    void debeRetornar201AlCrear() throws Exception {
        CrearReservaRequest request = buildRequest();

        when(reservaService.crear(any(CrearReservaRequest.class))).thenReturn(reservaResponse);

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("RES-ABCD1234"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.horaCheckin").value("15:00"))
                .andExpect(jsonPath("$.horaCheckout").value("12:00"));
    }

    @Test
    @DisplayName("POST /reservas - debe retornar 409 cuando habitación no está disponible")
    void debeRetornar409ConConflicto() throws Exception {
        CrearReservaRequest request = buildRequest();

        when(reservaService.crear(any())).thenThrow(new HabitacionNoDisponibleException(1L));

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /reservas - debe retornar 400 si falta habitacionId (validación Bean)")
    void debeRetornar400SinHabitacionId() throws Exception {
        CrearReservaRequest request = buildRequest();
        request.setHabitacionId(null); // campo requerido

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("POST /reservas - debe retornar 400 si capacidad excedida")
    void debeRetornar400CapacidadExcedida() throws Exception {
        CrearReservaRequest request = buildRequest();

        when(reservaService.crear(any())).thenThrow(
                new IllegalArgumentException("La habitación tiene capacidad máxima de 2 huéspedes."));

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/reservas/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /reservas/{id} - debe retornar 200 con la reserva")
    void debeRetornar200PorId() throws Exception {
        when(reservaService.obtenerPorId(1L)).thenReturn(reservaResponse);

        mockMvc.perform(get("/api/v1/reservas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.codigo").value("RES-ABCD1234"));
    }

    @Test
    @DisplayName("GET /reservas/{id} - debe retornar 404 si no existe")
    void debeRetornar404NoExiste() throws Exception {
        when(reservaService.obtenerPorId(99L)).thenThrow(new ReservaNotFoundException(99L));

        mockMvc.perform(get("/api/v1/reservas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/v1/reservas/hoy ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /reservas/hoy - debe retornar objeto con pendientesCheckin y pendientesCheckout")
    void debeRetornarReservasDelDia() throws Exception {
        Map<String, Object> hoy = new LinkedHashMap<>();
        hoy.put("fecha", LocalDate.now().toString());
        hoy.put("pendientesCheckin", List.of(reservaResponse));
        hoy.put("pendientesCheckout", Collections.emptyList());

        when(reservaService.getReservasHoy()).thenReturn(hoy);

        mockMvc.perform(get("/api/v1/reservas/hoy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientesCheckin").isArray())
                .andExpect(jsonPath("$.pendientesCheckout").isArray())
                .andExpect(jsonPath("$.pendientesCheckin[0].codigo").value("RES-ABCD1234"));
    }

    // ── PATCH /api/v1/reservas/{id}/estado ────────────────────────────────────

    @Test
    @DisplayName("PATCH /reservas/{id}/estado - debe retornar 200 en transición válida")
    void debeRetornar200CambioEstado() throws Exception {
        reservaResponse.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaService.cambiarEstado(1L, EstadoReserva.CONFIRMADA)).thenReturn(reservaResponse);

        mockMvc.perform(patch("/api/v1/reservas/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONFIRMADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("PATCH /reservas/{id}/estado - debe retornar 400 en transición inválida")
    void debeRetornar400TransicionInvalida() throws Exception {
        when(reservaService.cambiarEstado(anyLong(), any()))
                .thenThrow(new TransicionEstadoInvalidaException("CHECKOUT", "CHECKIN"));

        mockMvc.perform(patch("/api/v1/reservas/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CHECKIN\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/reservas/{id}/cancelar ──────────────────────────────────

    @Test
    @DisplayName("PATCH /reservas/{id}/cancelar - debe retornar 204 al cancelar exitosamente")
    void debeRetornar204AlCancelar() throws Exception {
        doNothing().when(reservaService).cancelar(1L);

        mockMvc.perform(patch("/api/v1/reservas/1/cancelar"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /reservas/{id}/cancelar - debe retornar 422 si está fuera del plazo de 24h")
    void debeRetornar422CancelacionFueraDePlazo() throws Exception {
        doThrow(new CancelacionRestringidaException(
                "Cancelación no permitida: menos de 24 horas para el check-in"))
                .when(reservaService).cancelar(1L);

        mockMvc.perform(patch("/api/v1/reservas/1/cancelar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("24 horas")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CrearReservaRequest buildRequest() {
        CrearReservaRequest req = new CrearReservaRequest();
        req.setUsuarioId(1L);
        req.setHabitacionId(1L);
        req.setFechaEntrada(LocalDate.now().plusDays(5));
        req.setFechaSalida(LocalDate.now().plusDays(7));
        req.setNumHuespedes(2);
        req.setFuente(FuenteReserva.DIRECTO);
        return req;
    }
}
