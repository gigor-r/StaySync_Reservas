package com.staysync.reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReservasServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReservasServiceApplication.class, args);
    }
}
