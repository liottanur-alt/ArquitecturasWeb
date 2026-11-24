package com.msvusuario.config;

import com.msvusuario.client.ArtistaClientFeign;
import com.msvusuario.client.EventoClientFeign;
import com.msvusuario.client.CompraClientFeign;
import com.msvusuario.client.InventarioClientFeign;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(clients = {ArtistaClientFeign.class, EventoClientFeign.class, CompraClientFeign.class, InventarioClientFeign.class})
public class FeignConfig {

    @Bean
    InternalAuthInterceptor internalAuthInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new InternalAuthInterceptor(serviceTokenProvider);
    }
}
