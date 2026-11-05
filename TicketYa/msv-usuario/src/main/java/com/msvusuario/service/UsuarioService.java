package com.msvusuario.service;

import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.dto.UsuarioResponse;
import com.msvusuario.entity.ArtistaFavorito;
import com.msvusuario.entity.ArtistaFavoritoID;
import com.msvusuario.entity.Usuario;
import com.msvusuario.repository.ArtistaFavoritoRepository;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final ArtistaFavoritoRepository favoritos;

    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(UsuarioResponse::from).toList();
    }

    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.from(buscar(id));
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        return guardar(new Usuario(), request);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = buscar(id);
        favoritos.deleteByUsuarioId(id);
        usuarios.delete(usuario);
    }

    public List<Long> listarFavoritos(Long idUsuario) {
        buscar(idUsuario);
        return favoritos.findByUsuarioId(idUsuario).stream().map(favorito -> favorito.getId().getIdArtista()).toList();
    }

    @Transactional
    public void agregarFavorito(Long idUsuario, Long idArtista) {
        Usuario usuario = buscar(idUsuario);
        validarIdArtista(idArtista);
        // El artista pertenece a otro microservicio; solo persistimos su identificador.
        favoritos.save(new ArtistaFavorito(usuario, idArtista));
    }

    @Transactional
    public void eliminarFavorito(Long idUsuario, Long idArtista) {
        buscar(idUsuario);
        validarIdArtista(idArtista);
        favoritos.deleteById(new ArtistaFavoritoID(idUsuario, idArtista));
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private UsuarioResponse guardar(Usuario usuario, UsuarioRequest request) {
        if (request == null || !textoValido(request.nombre(), 100) || !textoValido(request.email(), 254) || !textoValido(request.dni(), 20) || !textoValido(request.ciudad(), 100) || request.idRol() == null || request.idRol() <= 0 || !request.email().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, email valido, DNI, ciudad e idRol positivo son obligatorios");
        }
        usuario.setRol(roles.findById(request.idRol()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado")));
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(request.email().trim());
        usuario.setDni(request.dni().trim());
        usuario.setCiudad(request.ciudad().trim());
        return UsuarioResponse.from(usuarios.save(usuario));
    }

    private boolean textoValido(String texto, int longitudMaxima) {
        return texto != null && !texto.isBlank() && texto.trim().length() <= longitudMaxima;
    }

    private void validarIdArtista(Long idArtista) {
        if (idArtista == null || idArtista <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idArtista debe ser positivo");
        }
    }
}
