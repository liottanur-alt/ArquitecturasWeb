package org.msvcompraentradasol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.msvcompraentradasol.config.FeignClientConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication //escaneo de componentes
@EnableFeignClients(defaultConfiguration = FeignClientConfiguration.class)
public class MsvCompraEntradaSolApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                MsvCompraEntradaSolApplication.class, args
        );//levanta la app
    }
}
