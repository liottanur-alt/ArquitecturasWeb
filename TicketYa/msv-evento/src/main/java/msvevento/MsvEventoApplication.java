package msvevento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MsvEventoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsvEventoApplication.class, args);
    }

}
