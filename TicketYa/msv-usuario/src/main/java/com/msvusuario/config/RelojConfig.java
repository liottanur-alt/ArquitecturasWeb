package com.msvusuario.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelojConfig {
    @Bean
    public Clock relojEventos() {
        // Eventos publica fechas locales sin offset y usa esta misma zona horaria.
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
