package com.staysync.reservas.integration;

import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.HabitacionNoDisponibleException;
import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.Reserva.FuenteReserva;
import com.staysync.reservas.repository.ReservaRepository;
import com.staysync.reservas.service.ReservaService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de integración: Spring Boot completo con base de datos H2 en memoria.
 * Verifica que la capa de servicio, repositorio y transacciones funcionan correctas juntas.
 *
 * Usa el perfil "test" → application-test.yml con H2 en lugar de MySQL.
 * El RabbitMQ publisher se mockea para no requerir broker real.
 * RestTemplate se mockea para no depender de habitaciones-service.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional    // cada test revierte sus cambios → tests aislados
@DisplayName("ReservaService - Tests de Integración (H2)")
class ReservaIntegrationTest {

    @Autowired private ReservaService reservaService;
    @Autowired private ReservaRepository reservaRepository;

    // Mock de infraestructura externa
    @MockBean private com.staysync.reservas.messaging.ReservaEventPublisher eventPublisher;
    @MockBean private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Simular respuesta de habitaciones-service con habitación de capacidad 3
        Map<String, Object> habitacionData = Map.of(
                "id", 1,
                "precioPorNoche", 100.00,
                "capacidad", 3
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(habitacionData);
    }

    // ── Ciclo de vida de una reserva ──────────────────────────────────────────

    @Test
    @DisplayName("Debe persistir reserva en BD y asignar código único")
    void debePersistirReserva() {
        CrearReservaRequest request = buildRequest(1L, 1L,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), 2);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getCodigo()).matches("RES-[A-Z0-9]{8}");
        assertThat(response.getPrecioTotal()).isEqualByComparingTo(BigDecimal.valueOf(200)); // 100 * 2 noches
        assertThat(response.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);

        // Verificar que realmente quedó en la BD
        Optional<Reserva> enBd = reservaRepository.findById(response.getId());
        assertThat(enBd).isPresent();
        assertThat(enBd.get().getCodigo()).isEqualTo(response.getCodigo());
    }

    @Test
    @DisplayName("Debe rechazar reserva cuando hay conflicto de fechas para la misma habitación")
    void debeRechazarReservaPorConflicto() {
        LocalDate entrada = LocalDate.now().plusDays(10);
        LocalDate salida  = LocalDate.now().plusDays(12);

        // Primera reserva — exitosa
        CrearReservaRequest primera = buildRequest(1L, 2L, entrada, salida, 1);
        ReservaResponse primeraResp = reservaService.crear(primera);

        // Confirmar la primera para que bloquee la habitación
        reservaService.cambiarEstado(primeraResp.getId(), EstadoReserva.CONFIRMADA);

        // Segunda reserva con las mismas fechas y habitación → conflicto
        CrearReservaRequest segunda = buildRequest(2L, 2L, entrada, salida, 1);

        assertThatThrownBy(() -> reservaService.crear(segunda))
                .isInstanceOf(HabitacionNoDisponibleException.class);
    }

    @Test
    @DisplayName("Transición completa PENDIENTE → CONFIRMADA → CHECKIN → CHECKOUT")
    void debePasarPorTodosLosEstados() {
        ReservaResponse creada = reservaService.crear(
                buildRequest(1L, 3L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), 2));
        Long id = creada.getId();

        reservaService.cambiarEstado(id, EstadoReserva.CONFIRMADA);
        assertThat(reservaRepository.findById(id).get().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);

        reservaService.cambiarEstado(id, EstadoReserva.CHECKIN);
        assertThat(reservaRepository.findById(id).get().getEstado()).isEqualTo(EstadoReserva.CHECKIN);

        reservaService.cambiarEstado(id, EstadoReserva.CHECKOUT);
        assertThat(reservaRepository.findById(id).get().getEstado()).isEqualTo(EstadoReserva.CHECKOUT);

        verify(eventPublisher).publicar(eq("reserva.confirmada"), anyMap());
        verify(eventPublisher).publicar(eq("reserva.checkin"),    anyMap());
        verify(eventPublisher).publicar(eq("reserva.checkout"),   anyMap());
    }

    @Test
    @DisplayName("autoTransicionarACheckin() debe cambiar estado en BD para reservas de hoy")
    void debeAutoTransicionarEnBd() {
        // Crear y confirmar una reserva con entrada hoy
        CrearReservaRequest request = buildRequest(1L, 4L,
                LocalDate.now(), LocalDate.now().plusDays(2), 1);
        ReservaResponse creada = reservaService.crear(request);
        reservaService.cambiarEstado(creada.getId(), EstadoReserva.CONFIRMADA);

        reservaService.autoTransicionarACheckin();

        Reserva enBd = reservaRepository.findById(creada.getId()).get();
        assertThat(enBd.getEstado()).isEqualTo(EstadoReserva.CHECKIN);
    }

    @Test
    @DisplayName("getReservasHoy() debe separar correctamente los pendientes de check-in y check-out")
    void debeRetornarReservasDelDiaDesdeRealBd() {
        // Reserva de check-in (CONFIRMADA con entrada hoy)
        ReservaResponse checkin = reservaService.crear(
                buildRequest(1L, 5L, LocalDate.now(), LocalDate.now().plusDays(2), 1));
        reservaService.cambiarEstado(checkin.getId(), EstadoReserva.CONFIRMADA);

        // Reserva de check-out (ya en CHECKIN)
        ReservaResponse checkout = reservaService.crear(
                buildRequest(2L, 6L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 1));
        reservaService.cambiarEstado(checkout.getId(), EstadoReserva.CONFIRMADA);
        reservaService.cambiarEstado(checkout.getId(), EstadoReserva.CHECKIN);

        Map<String, Object> hoy = reservaService.getReservasHoy();

        @SuppressWarnings("unchecked")
        var pendientesCheckin  = (java.util.List<?>) hoy.get("pendientesCheckin");
        @SuppressWarnings("unchecked")
        var pendientesCheckout = (java.util.List<?>) hoy.get("pendientesCheckout");

        assertThat(pendientesCheckin).isNotEmpty();
        assertThat(pendientesCheckout).isNotEmpty();
    }

    @Test
    @DisplayName("listar() debe paginar correctamente con múltiples reservas en BD")
    void debePaginarReservas() {
        for (int i = 0; i < 5; i++) {
            reservaService.crear(buildRequest((long)(i + 1), (long)(i + 10),
                    LocalDate.now().plusDays(i + 5), LocalDate.now().plusDays(i + 7), 1));
        }

        Page<ReservaResponse> pagina1 = reservaService.listar(PageRequest.of(0, 3));
        Page<ReservaResponse> pagina2 = reservaService.listar(PageRequest.of(1, 3));

        assertThat(pagina1.getContent()).hasSize(3);
        assertThat(pagina1.getTotalElements()).isEqualTo(5);
        assertThat(pagina2.getContent()).hasSize(2);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private CrearReservaRequest buildRequest(Long usuarioId, Long habitacionId,
                                              LocalDate entrada, LocalDate salida,
                                              int numHuespedes) {
        CrearReservaRequest req = new CrearReservaRequest();
        req.setUsuarioId(usuarioId);
        req.setHabitacionId(habitacionId);
        req.setFechaEntrada(entrada);
        req.setFechaSalida(salida);
        req.setNumHuespedes(numHuespedes);
        req.setFuente(FuenteReserva.DIRECTO);
        return req;
    }
}
