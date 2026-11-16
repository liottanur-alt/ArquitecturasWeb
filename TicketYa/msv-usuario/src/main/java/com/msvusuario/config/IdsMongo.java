package com.msvusuario.config;

import com.msvusuario.entity.Rol;
import com.msvusuario.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

/** Conserva los IDs numericos de la API sin depender del AUTO_INCREMENT de MySQL. */
@Component
@RequiredArgsConstructor
public class IdsMongo implements BeforeConvertCallback<Object> {
    // El callback se registra durante la creacion del converter; resolver el template
    // al generar el ID evita una dependencia circular durante el arranque.
    private final ObjectProvider<MongoTemplate> mongo;

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        // Spring lo ejecuta antes de convertir el objeto a BSON para guardarlo.
        // Solo asigna un ID a objetos nuevos; las actualizaciones conservan el suyo.
        if (entity instanceof Usuario usuario && usuario.getId() == null) {
            usuario.setId(siguiente("usuarios"));
        } else if (entity instanceof Rol rol && rol.getId() == null) {
            rol.setId(siguiente("roles"));
        }
        return entity;
    }

    private long siguiente(String nombre) {
        // secuencias tiene un contador por coleccion, identificado por su nombre.
        // findAndModify incrementa y devuelve el contador en una operacion atomica.
        // upsert lo crea si no existe; returnNew devuelve el valor ya incrementado.
        Document contador = mongo.getObject().findAndModify(
                Query.query(Criteria.where("_id").is(nombre)),
                new Update().inc("valor", 1L),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class, "secuencias");
        if (contador == null) {
            throw new IllegalStateException("No se pudo generar el ID de " + nombre);
        }
        return ((Number) contador.get("valor")).longValue();
    }
}
