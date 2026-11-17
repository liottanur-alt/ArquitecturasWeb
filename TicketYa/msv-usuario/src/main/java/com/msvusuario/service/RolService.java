package com.msvusuario.service;

import com.msvusuario.dto.RolRequest;
import com.msvusuario.entity.Rol;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RolService {
    private final RolRepository roles;
    private final UsuarioRepository usuarios;

    public List<Rol> listar() {
        return roles.findAll();
    }

    public Rol obtener(Long id) {
        return roles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
    }
    public Rol crear(RolRequest request) {
        return guardar(new Rol(), request);
    }
    public Rol actualizar(Long id, RolRequest request) {
        return guardar(obtener(id), request);
    }
    public void eliminar(Long id) {
        Rol rol = obtener(id);
        // MongoDB no tiene FK: la aplicacion comprueba que el rol no este en uso.
        // Esta consulta y el borrado son operaciones separadas, sin transaccion conjunta.
        if (usuarios.existsByRolId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El rol tiene usuarios asociados");
        }
        roles.delete(rol);
    }

    private Rol guardar(Rol rol, RolRequest request) {
        if (request == null || request.name() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de rol es obligatorio");
        }
        rol.setName(request.name());
        return roles.save(rol);
    }
}
