package com.universidad.msvsectoerasiento.config;

import com.universidad.msvsectoerasiento.client.*;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(clients = {EventoClientFeign.class, CompraClientFeign.class})
public class FeignConfig {
}
