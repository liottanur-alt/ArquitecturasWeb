package com.msvusuario.config;

import com.msvusuario.client.ArtistaClientFeign;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(clients = ArtistaClientFeign.class)
public class FeignConfig {
}
