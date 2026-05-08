package com.staysync.reservas.service;

import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.*;
import com.staysync.reservas.messaging.ReservaEventPublisher;
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
        reservaBase = Reserva.builder()
                .id(1L).codigo("RES-ABCD1234")
                .usuarioId(1L).habitacionId(1L)
                .fechaEntrada(LocalDate.now().plusDays(1))
                .fechaSalida(LocalDate.now().plusDays(3))
                .numHuespedes(2)
                .estado(EstadoReserva.PENDIENTE)
                .precioTotal(BigDecimal.valueOf(200))
                .fuente(FuenteReserva.DIRECTO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("crear() - debe crear reserva cuando no hay conflicto")
    void debeCrearReserva() {
        CrearReservaRequest request = new CrearReservaRequest();
        request.setUsuarioId(1L);
        request.setHabitacionId(1L);
        request.setFechaEntrada(LocalDate.now().plusDays(1));
        request.setFechaSalida(LocalDate.now().plusDays(3));
        request.setNumHuespedes(2);
        request.setFuente(FuenteReserva.DIRECTO);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response).isNotNull();
        assertThat(response.getCodigo()).isNotBlank();
        verify(eventPublisher).publicar(eq("reserva.confirmada"), anyMap());
    }

    @Test
    @DisplayName("crear() - debe lanzar excepción si hay conflicto de fechas")
    void debeLanzarExcepcionConConflicto() {
        CrearReservaRequest request = new CrearReservaRequest();
        request.setHabitacionId(1L);
        request.setFechaEntrada(LocalDate.now().plusDays(1));
        request.setFechaSalida(LocalDate.now().plusDays(3));
        request.setFuente(FuenteReserva.DIRECTO);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(HabitacionNoDisponibleException.class);

        verify(reservaRepository, never()).save(any());
    }

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
    @DisplayName("cambiarEstado() - debe cambiar PENDIENTE → CONFIRMADA")
    void debeCambiarEstadoPendienteAConfirmada() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cambiarEstado(1L, EstadoReserva.CONFIRMADA);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(eventPublisher).publicar(eq("reserva.confirmada"), anyMap());
    }

    @Test
    @DisplayName("cambiarEstado() - debe lanzar excepción en transición inválida CHECKOUT → CHECKIN")
    void debeLanzarExcepcionTransicionInvalida() {
        reservaBase.setEstado(EstadoReserva.CHECKOUT);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cambiarEstado(1L, EstadoReserva.CHECKIN))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    @Test
    @DisplayName("cancelar() - debe cancelar reserva CONFIRMADA y publicar evento")
    void debeCancelarReserva() {
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cancelar(1L);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        verify(eventPublisher).publicar(eq("reserva.cancelada"), anyMap());
    }

    @Test
    @DisplayName("listar() - debe retornar página de reservas")
    void debeListarReservas() {
        Pageable pageable = PageRequest.of(0, 10);
        when(reservaRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(reservaBase)));

        Page<ReservaResponse> result = reservaService.listar(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
