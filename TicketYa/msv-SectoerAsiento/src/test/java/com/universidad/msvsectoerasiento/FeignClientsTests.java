package com.universidad.msvsectoerasiento;

import com.universidad.msvsectoerasiento.client.*;
import com.universidad.msvsectoerasiento.config.FeignConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class FeignClientsTests {
    @Test
    void registraClientesSinContactarProveedores() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
                .withUserConfiguration(FeignConfig.class)
                .withPropertyValues("msvc.eventos.url=http://localhost:18001",
                        "msvc.compras.url=http://localhost:18003")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EventoClientFeign.class)).isNotNull();
                    assertThat(context.getBean(CompraClientFeign.class)).isNotNull();
                });
    }

    @Test
    void contratosEventosUsanGetYParametroDeRuta() {
        var metodos = new SpringMvcContract().parseAndValidateMetadata(EventoClientFeign.class);
        assertThat(metodos).hasSize(2);
        assertThat(metodos).allSatisfy(metodo -> {
            assertThat(metodo.template().method()).isEqualTo("GET");
            assertThat(metodo.template().url()).isIn("/api/eventos/{id}", "/api/lugares/{id}");
            assertThat(metodo.indexToName().get(0)).contains("id");
        });
    }

    @Test
    void contratosComprasUsanGetYParametroDeRuta() {
        var metodos = new SpringMvcContract().parseAndValidateMetadata(CompraClientFeign.class);
        assertThat(metodos).hasSize(2);
        assertThat(metodos).allSatisfy(metodo -> {
            assertThat(metodo.template().method()).isEqualTo("GET");
            assertThat(metodo.template().url()).isIn("/compras/{id}", "/entradas/{id}");
            assertThat(metodo.indexToName().get(0)).contains("id");
        });
    }
}
