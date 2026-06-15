package com.staysync.reservas.service;

import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.*;
import com.staysync.reservas.messaging.ReservaEventPublisher;
import com.staysync.reservas.model.HuespedAdicional;
import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.Reserva.FuenteReserva;
import com.staysync.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservaService - Tests Unitarios")
class ReservaServiceTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private ReservaEventPublisher eventPublisher;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private ReservaService reservaService;

    private Reserva reservaBase;

    @BeforeEach
    void setUp() {
        // FIX Bug 1: plusDays(5) garantiza siempre > 24h de anticipación
        // Con plusDays(1) el test fallaba en la tarde (hora Chile) porque
        // horasRestantes < 24 → CancelacionRestringidaException inesperada
        reservaBase = Reserva.builder()
                .id(1L).codigo("RES-ABCD1234")
                .usuarioId(1L).habitacionId(1L)
                .fechaEntrada(LocalDate.now().plusDays(5))
                .fechaSalida(LocalDate.now().plusDays(7))
                .numHuespedes(2)
                .estado(EstadoReserva.PENDIENTE)
                .precioTotal(BigDecimal.valueOf(200))
                .fuente(FuenteReserva.DIRECTO)
                .huespedesAdicionales(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── CREAR ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear() - debe crear reserva con precio de habitación cuando el servicio responde")
    void debeCrearReservaConPrecioReal() {
        CrearReservaRequest request = buildRequest(2);

        // FIX Bug 2: mockear restTemplate para que el test cubra el camino real
        Map<String, Object> habitacionData = new HashMap<>();
        habitacionData.put("precioPorNoche", 90.0);
        habitacionData.put("capacidad", 3);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(habitacionData);
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response).isNotNull();
        assertThat(response.getCodigo()).isNotBlank();
        verify(eventPublisher).publicar(eq("reserva.confirmada"), anyMap());
    }

    @Test
    @DisplayName("crear() - debe usar precio por defecto cuando habitaciones-service no responde")
    void debeCrearReservaConPrecioPorDefecto() {
        CrearReservaRequest request = buildRequest(2);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response).isNotNull();
        verify(reservaRepository).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear() - debe lanzar excepción si numHuespedes supera la capacidad de la habitación")
    void debeLanzarExcepcionCapacidadExcedida() {
        CrearReservaRequest request = buildRequest(5); // pide 5 huéspedes

        Map<String, Object> habitacionData = new HashMap<>();
        habitacionData.put("capacidad", 3); // capacidad máxima 3
        habitacionData.put("precioPorNoche", 100.0);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(habitacionData);

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacidad máxima");

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear() - debe lanzar excepción si hay conflicto de fechas")
    void debeLanzarExcepcionConConflicto() {
        CrearReservaRequest request = buildRequest(2);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(HabitacionNoDisponibleException.class);

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear() - debe guardar huéspedes adicionales cuando se incluyen en el request")
    void debeGuardarHuespedesAdicionales() {
        CrearReservaRequest.HuespedAdicionalRequest h = new CrearReservaRequest.HuespedAdicionalRequest();
        h.setNombre("María");
        h.setApellido("López");
        h.setDocumento("12345678");

        CrearReservaRequest request = buildRequest(2);
        request.setHuespedesAdicionales(List.of(h));

        Reserva reservaConHuespedes = reservaBase.toBuilder()
                .huespedesAdicionales(new ArrayList<>(List.of(
                        HuespedAdicional.builder().nombre("María").apellido("López").build()
                ))).build();

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);
        when(reservaRepository.save(any())).thenReturn(reservaBase)
                                           .thenReturn(reservaConHuespedes);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response).isNotNull();
        // save se llama 2 veces: 1a para la reserva, 2a para los huéspedes adicionales
        verify(reservaRepository, times(2)).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear() - debe lanzar excepción si fechaSalida no es posterior a fechaEntrada")
    void debeLanzarExcepcionFechasInvalidas() {
        CrearReservaRequest request = new CrearReservaRequest();
        request.setUsuarioId(1L);
        request.setHabitacionId(1L);
        request.setFechaEntrada(LocalDate.now().plusDays(5));
        request.setFechaSalida(LocalDate.now().plusDays(5)); // igual, no posterior
        request.setNumHuespedes(2);
        request.setFuente(FuenteReserva.DIRECTO);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
    }

    // ── OBTENER ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerPorId() - debe retornar reserva existente")
    void debeRetornarReserva() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        ReservaResponse response = reservaService.obtenerPorId(1L);

        assertThat(response).isNotNull();
        assertThat(response.getCodigo()).isEqualTo("RES-ABCD1234");
    }

    @Test
    @DisplayName("obtenerPorId() - debe lanzar excepción si no existe")
    void debeLanzarExcepcionNoExiste() {
        when(reservaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.obtenerPorId(99L))
                .isInstanceOf(ReservaNotFoundException.class);
    }

    @Test
    @DisplayName("listar() - debe retornar página de reservas")
    void debeListarReservas() {
        Pageable pageable = PageRequest.of(0, 10);
        when(reservaRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(reservaBase)));

        Page<ReservaResponse> result = reservaService.listar(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── CAMBIAR ESTADO ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cambiarEstado() - debe cambiar PENDIENTE → CONFIRMADA y publicar evento")
    void debeCambiarEstadoPendienteAConfirmada() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cambiarEstado(1L, EstadoReserva.CONFIRMADA);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(eventPublisher).publicar(eq("reserva.confirmada"), anyMap());
    }

    @Test
    @DisplayName("cambiarEstado() - debe cambiar CONFIRMADA → CHECKIN y publicar evento")
    void debeCambiarEstadoConfirmadaACheckin() {
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cambiarEstado(1L, EstadoReserva.CHECKIN);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CHECKIN);
        verify(eventPublisher).publicar(eq("reserva.checkin"), anyMap());
    }

    @Test
    @DisplayName("cambiarEstado() - debe lanzar excepción en transición inválida CHECKOUT → CHECKIN")
    void debeLanzarExcepcionTransicionInvalida() {
        reservaBase.setEstado(EstadoReserva.CHECKOUT);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cambiarEstado(1L, EstadoReserva.CHECKIN))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    // ── CANCELAR ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelar() - debe cancelar reserva CONFIRMADA con > 24h de anticipación")
    void debeCancelarReserva() {
        // FIX Bug 1: reservaBase tiene fechaEntrada en 5 días → siempre > 24h
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cancelar(1L);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        verify(eventPublisher).publicar(eq("reserva.cancelada"), anyMap());
    }

    @Test
    @DisplayName("cancelar() - debe lanzar CancelacionRestringidaException con < 24h de anticipación")
    void debeLanzarExcepcionCancelacionFueraDePlazo() {
        // Check-in es mañana temprano → menos de 24h de anticipación
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        reservaBase.setFechaEntrada(LocalDate.now()); // hoy mismo (check-in pasado)
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cancelar(1L))
                .isInstanceOf(CancelacionRestringidaException.class)
                .hasMessageContaining("24 horas");

        verify(reservaRepository, never()).save(any());
        verify(eventPublisher, never()).publicar(anyString(), anyMap());
    }

    @Test
    @DisplayName("cancelar() - debe lanzar excepción si la reserva ya está CANCELADA")
    void debeLanzarExcepcionSiYaCancelada() {
        reservaBase.setEstado(EstadoReserva.CANCELADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cancelar(1L))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    @Test
    @DisplayName("cancelar() - debe lanzar excepción si la reserva ya hizo CHECKOUT")
    void debeLanzarExcepcionSiYaCheckout() {
        reservaBase.setEstado(EstadoReserva.CHECKOUT);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cancelar(1L))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    // ── SCHEDULER ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("autoTransicionarACheckin() - debe cambiar a CHECKIN las reservas CONFIRMADAS de hoy")
    void debeAutoTransicionarReservasConfirmadasDeHoy() {
        Reserva reservaConfirmada = reservaBase.toBuilder()
                .estado(EstadoReserva.CONFIRMADA)
                .fechaEntrada(LocalDate.now())
                .build();

        when(reservaRepository.findConfirmadasParaAutoCheckin(LocalDate.now()))
                .thenReturn(List.of(reservaConfirmada));
        when(reservaRepository.save(any())).thenReturn(reservaConfirmada);

        reservaService.autoTransicionarACheckin();

        assertThat(reservaConfirmada.getEstado()).isEqualTo(EstadoReserva.CHECKIN);
        verify(reservaRepository).save(reservaConfirmada);
        verify(eventPublisher).publicar(eq("reserva.checkin"), anyMap());
    }

    @Test
    @DisplayName("autoTransicionarACheckin() - no debe hacer nada si no hay reservas para hoy")
    void noDebeHacerNadaSinReservasParaHoy() {
        when(reservaRepository.findConfirmadasParaAutoCheckin(LocalDate.now()))
                .thenReturn(Collections.emptyList());

        reservaService.autoTransicionarACheckin();

        verify(reservaRepository, never()).save(any());
        verify(eventPublisher, never()).publicar(anyString(), anyMap());
    }

    @Test
    @DisplayName("autoTransicionarACheckin() - debe procesar múltiples reservas y publicar evento por cada una")
    void debeProcessarMultiplesReservasEnAutoCheckin() {
        Reserva r1 = reservaBase.toBuilder().id(1L).estado(EstadoReserva.CONFIRMADA).build();
        Reserva r2 = reservaBase.toBuilder().id(2L).estado(EstadoReserva.CONFIRMADA)
                .codigo("RES-XYZ99999").build();

        when(reservaRepository.findConfirmadasParaAutoCheckin(LocalDate.now()))
                .thenReturn(List.of(r1, r2));
        when(reservaRepository.save(any())).thenReturn(r1).thenReturn(r2);

        reservaService.autoTransicionarACheckin();

        verify(reservaRepository, times(2)).save(any());
        verify(eventPublisher, times(2)).publicar(eq("reserva.checkin"), anyMap());
    }

    // ── GET RESERVAS HOY ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getReservasHoy() - debe separar pendientes de check-in y check-out activos")
    void debeRetornarReservasDelDia() {
        Reserva checkinPendiente = reservaBase.toBuilder()
                .estado(EstadoReserva.CONFIRMADA)
                .fechaEntrada(LocalDate.now())
                .build();
        Reserva checkoutActivo = reservaBase.toBuilder()
                .id(2L).codigo("RES-ZZ999999")
                .estado(EstadoReserva.CHECKIN)
                .build();

        when(reservaRepository.findByEstadoAndFechaEntrada(EstadoReserva.CONFIRMADA, LocalDate.now()))
                .thenReturn(List.of(checkinPendiente));
        when(reservaRepository.findByEstado(EstadoReserva.CHECKIN))
                .thenReturn(List.of(checkoutActivo));

        Map<String, Object> result = reservaService.getReservasHoy();

        assertThat(result).containsKey("pendientesCheckin").containsKey("pendientesCheckout");
        @SuppressWarnings("unchecked")
        List<ReservaResponse> checkins = (List<ReservaResponse>) result.get("pendientesCheckin");
        @SuppressWarnings("unchecked")
        List<ReservaResponse> checkouts = (List<ReservaResponse>) result.get("pendientesCheckout");
        assertThat(checkins).hasSize(1);
        assertThat(checkouts).hasSize(1);
    }

    @Test
    @DisplayName("getReservasHoy() - debe retornar listas vacías si no hay reservas")
    void debeRetornarListasVaciasConSinReservas() {
        when(reservaRepository.findByEstadoAndFechaEntrada(any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservaRepository.findByEstado(any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = reservaService.getReservasHoy();

        @SuppressWarnings("unchecked")
        List<?> checkins = (List<?>) result.get("pendientesCheckin");
        assertThat(checkins).isEmpty();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private CrearReservaRequest buildRequest(int numHuespedes) {
        CrearReservaRequest req = new CrearReservaRequest();
        req.setUsuarioId(1L);
        req.setHabitacionId(1L);
        req.setFechaEntrada(LocalDate.now().plusDays(5));
        req.setFechaSalida(LocalDate.now().plusDays(7));
        req.setNumHuespedes(numHuespedes);
        req.setFuente(FuenteReserva.DIRECTO);
        return req;
    }
}
