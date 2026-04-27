package com.staysync.reservas.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaEventPublisher {

    private static final String EXCHANGE = "staysync.reservas.exchange";
    private final RabbitTemplate rabbitTemplate;

    public void publicar(String routingKey, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
            log.info("Evento publicado: {} -> {}", EXCHANGE, routingKey);
        } catch (Exception e) {
            log.error("Error publicando evento {}: {}", routingKey, e.getMessage());
        }
    }
}
