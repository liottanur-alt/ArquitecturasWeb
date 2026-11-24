package com.msvusuario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cada instancia se guarda como un documento de la coleccion usuarios.
@Document(collection = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    // MongoDB lo guarda como _id; IdsMongo genera su valor numerico.
    @Id
    private Long id;

    // Referencia un rol compartido; no crea una clave foranea ni guarda el rol en cascada.
    @DBRef
    private Rol rol;
    private String nombre;

    // El indice hace que MongoDB rechace emails duplicados.
    @Indexed(unique = true)
    private String email;

    // Hash BCrypt. Las cuentas creadas antes de la autenticacion no lo tienen.
    @JsonIgnore
    private String passwordHash;

    // La unicidad del DNI tambien se comprueba en la base, incluso con altas simultaneas.
    @Indexed(unique = true)
    private String dni;
    private String ciudad;

    // Se guarda como un array dentro del usuario, sin coleccion de favoritos aparte.
    // Solo contiene IDs: nombre y genero se consultan al servicio de eventos.
    private Set<Long> artistasFavoritos = new LinkedHashSet<>();

    // Preferencias embebidas en MongoDB, independientes de la ciudad del perfil.
    private Set<TipoEvento> tiposEventoPreferidos = new LinkedHashSet<>();
    private String ciudadPreferida = "";

    // Impide que guardar un perfil desactualizado sobrescriba cambios concurrentes.
    @Version
    private Long version;
}
