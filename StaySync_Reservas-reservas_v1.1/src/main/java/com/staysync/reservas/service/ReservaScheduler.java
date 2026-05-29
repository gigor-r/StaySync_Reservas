package com.staysync.reservas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaScheduler {

    private final ReservaService reservaService;

    /**
     * Ejecuta la auto-transición al inicio para cubrir cualquier reserva
     * que debió pasar a CHECKIN mientras el servicio estaba caído.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ejecutarAlArranque() {
        log.info("Ejecutando auto-checkin al arranque del servicio...");
        reservaService.autoTransicionarACheckin();
    }
}
