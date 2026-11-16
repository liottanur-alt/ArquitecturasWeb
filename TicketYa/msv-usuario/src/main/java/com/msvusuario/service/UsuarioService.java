package com.msvusuario.service;

import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.dto.UsuarioResponse;
import com.msvusuario.dto.ArtistaResponse;
import com.msvusuario.client.ArtistaClientFeign;
import feign.FeignException;
import com.msvusuario.entity.Usuario;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    // Permite modificar solo el array de favoritos sin reemplazar todo el documento.
    private final MongoTemplate mongo;
    private final ArtistaClientFeign artistas;

    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(UsuarioResponse::from).toList();
    }

    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.from(buscar(id));
    }
    public UsuarioResponse crear(UsuarioRequest request) {
        return guardar(new Usuario(), request);
    }
    public UsuarioResponse actualizar(Long id, UsuarioRequest request) {
        return guardar(buscar(id), request);
    }
    public void eliminar(Long id) {
        Usuario usuario = buscar(id);
        // Los favoritos pertenecen al mismo documento: se eliminan junto al usuario.
        usuarios.delete(usuario);
    }

    public List<ArtistaResponse> listarFavoritos(Long idUsuario) {
        return buscar(idUsuario).getArtistasFavoritos().stream()
                .map(this::obtenerArtista).toList();
    }

    private ArtistaResponse obtenerArtista(Long idArtista) {
        try {
            ArtistaResponse artista = artistas.obtenerPorId(idArtista);
            if (artista == null || artista.nombre() == null || artista.nombre().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El servicio de artistas devolvio datos incompletos para el artista " + idArtista);
            }
            // El ID almacenado en el favorito identifica el recurso consultado.
            return new ArtistaResponse(idArtista, artista.nombre(), artista.genero());
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El artista favorito " + idArtista + " ya no existe en el catalogo");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudieron consultar los artistas favoritos. Intenta nuevamente mas tarde");
        }
    }
    public void agregarFavorito(Long idUsuario, Long idArtista) {
        validarIdArtista(idArtista);
        // $addToSet agrega el ID sin duplicarlo, incluso con peticiones concurrentes.
        actualizarFavoritos(idUsuario, new Update().addToSet("artistasFavoritos", idArtista));
    }
    public void eliminarFavorito(Long idUsuario, Long idArtista) {
        validarIdArtista(idArtista);
        // $pull quita el ID del array; si no estaba, la lista queda igual.
        actualizarFavoritos(idUsuario, new Update().pull("artistasFavoritos", idArtista));
    }

    private void actualizarFavoritos(Long idUsuario, Update update) {
        // No hay upsert: agregar un favorito nunca crea un usuario inexistente.
        // Spring traduce id a _id e incrementa @Version al actualizar el documento.
        var resultado = mongo.updateFirst(Query.query(Criteria.where("id").is(idUsuario)),
                update, Usuario.class);
        // Se comprueba coincidencia, no cambios: repetir un favorito tambien es valido.
        if (resultado.getMatchedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private UsuarioResponse guardar(Usuario usuario, UsuarioRequest request) {
        if (request == null || !textoValido(request.nombre(), 100) || !textoValido(request.email(), 254) || !textoValido(request.dni(), 20) || !textoValido(request.ciudad(), 100) || request.idRol() == null || request.idRol() <= 0 || !request.email().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, email valido, DNI, ciudad e idRol positivo son obligatorios");
        }
        // DBRef no valida la existencia del rol: se comprueba antes de guardar el usuario.
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
