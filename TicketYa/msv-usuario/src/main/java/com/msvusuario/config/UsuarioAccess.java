package com.msvusuario.config;

import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("usuarioAccess")
@RequiredArgsConstructor
public class UsuarioAccess {
    private final UsuarioRepository usuarios;

    @Transactional(readOnly = true)
    public boolean puedeActualizar(Long id, UsuarioRequest request, Authentication auth) {
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) return true;
        if (!id.toString().equals(auth.getName()) || request == null) return false;
        // Un usuario puede editar su perfil, pero no asignarse otro rol.
        return usuarios.findById(id).map(u -> u.getRol().getId().equals(request.idRol())).orElse(false);
    }
}
