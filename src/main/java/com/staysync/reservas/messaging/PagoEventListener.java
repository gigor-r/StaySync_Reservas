package com.staysync.reservas.messaging;

import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.service.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PagoEventListener {

    private final ReservaService reservaService;

    @RabbitListener(queues = "q.pago.completado")
    public void onPagoCompletado(Map<String, Object> evento) {
        try {
            Long reservaId = Long.valueOf(evento.get("reservaId").toString());
            reservaService.cambiarEstado(reservaId, EstadoReserva.CONFIRMADA);
            log.info("Reserva {} confirmada por pago completado", reservaId);
        } catch (Exception e) {
            log.error("Error procesando evento pago.completado: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = "q.pago.fallido")
    public void onPagoFallido(Map<String, Object> evento) {
        Long reservaId = Long.valueOf(evento.get("reservaId").toString());
        log.warn("Pago fallido para reserva {}. Permanece en PENDIENTE.", reservaId);
    }
}
