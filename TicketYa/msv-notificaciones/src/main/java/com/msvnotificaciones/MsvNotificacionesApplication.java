package com.msvnotificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MsvNotificacionesApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsvNotificacionesApplication.class, args);
    }
}
