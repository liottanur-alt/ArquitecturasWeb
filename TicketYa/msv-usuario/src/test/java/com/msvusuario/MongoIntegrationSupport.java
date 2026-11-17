package com.msvusuario;

import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Usa MongoDB real con una base exclusiva de pruebas, nunca la base usuarios. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class MongoIntegrationSupport {
    private static final String DATABASE = "usuarios_test_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.database", () -> DATABASE);
    }

    @AfterAll
    static void limpiarBase(@Autowired MongoTemplate mongo) {
        if (!mongo.getDb().getName().equals(DATABASE)) {
            throw new IllegalStateException("La base no es la base aislada de pruebas");
        }
        mongo.getDb().drop();
    }
}
