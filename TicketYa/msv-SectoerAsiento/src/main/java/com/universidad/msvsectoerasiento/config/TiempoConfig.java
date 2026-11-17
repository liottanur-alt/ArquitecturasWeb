package com.universidad.msvsectoerasiento.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling
public class TiempoConfig {
    /** Reloj inyectable para probar vencimientos sin esperas reales. */
    @Bean public Clock reloj() { return Clock.systemUTC(); }
}
