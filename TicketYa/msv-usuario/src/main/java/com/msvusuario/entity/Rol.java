package com.msvusuario.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Coleccion separada porque varios usuarios pueden referenciar el mismo rol.
@Document(collection = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Rol {
    // Se guarda como _id; el contador IdsMongo conserva los IDs numericos de la API.
    @Id
    private Long id;
    // Un solo documento por tipo de rol; el enum se guarda por nombre.
    @Indexed(unique = true)
    private TipoRol name;


}
