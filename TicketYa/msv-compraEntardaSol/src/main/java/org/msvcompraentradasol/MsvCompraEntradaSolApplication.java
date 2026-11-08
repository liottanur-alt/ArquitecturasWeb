package org.msvcompraentradasol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication //escaneo de componentes
public class MsvCompraEntradaSolApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                MsvCompraEntradaSolApplication.class, args
        );//levanta la app
    }
}