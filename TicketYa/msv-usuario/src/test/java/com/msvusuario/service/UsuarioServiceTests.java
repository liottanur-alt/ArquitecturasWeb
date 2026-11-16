package com.msvusuario.service;

import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.entity.Usuario;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTests {
    private final com.msvusuario.client.ArtistaClientFeign artistas = mock(com.msvusuario.client.ArtistaClientFeign.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final UsuarioService service = new UsuarioService(usuarios, roles, mongo, artistas);

    @Test
    void favoritosIncluyenNombreYGeneroDelServicioDeArtistas() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.getArtistasFavoritos().add(8L); when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
        var artista = new com.msvusuario.dto.ArtistaResponse(8L, "Soda Stereo", "Rock");
        when(artistas.obtenerPorId(8L)).thenReturn(artista);
        assertEquals(java.util.List.of(artista), service.listarFavoritos(1L));
    }

    @Test
    void sinFavoritosNoConsultaElServicioExterno() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        assertTrue(service.listarFavoritos(1L).isEmpty());
        verifyNoInteractions(artistas);
    }

    @Test
    void falloDelServicioExternoDevuelve503SinBorrarFavoritos() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.getArtistasFavoritos().add(8L); when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
        feign.FeignException fallo = mock(feign.FeignException.class);
        when(fallo.status()).thenReturn(500);
        when(artistas.obtenerPorId(8L)).thenThrow(fallo);
        var error = assertThrows(ResponseStatusException.class, () -> service.listarFavoritos(1L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verifyNoInteractions(mongo);
    }

    @Test
    void eliminarUsuarioEliminaElDocumentoConSusFavoritos() {
        Usuario usuario = new Usuario();
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
        service.eliminar(1L);
        verify(usuarios).delete(usuario); verifyNoInteractions(mongo);
    }

    @Test
    void usuarioInexistenteNoPermiteAgregarFavoritos() {
        when(mongo.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Usuario.class))).thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0, 0L, null));
        var error = assertThrows(ResponseStatusException.class, () -> service.agregarFavorito(1L, 8L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(usuarios, never()).save(any());
    }

    @Test
    void emailInvalidoNoSeGuarda() {
        var request = new UsuarioRequest("Ana", "incorrecto", "12345678", "Tandil", 1L);
        var error = assertThrows(ResponseStatusException.class, () -> service.crear(request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(usuarios, never()).save(any());
    }

    @Test
    void artistaEliminadoDevuelve502SinModificarFavoritos() {
        Usuario usuario = new Usuario();
        usuario.getArtistasFavoritos().add(8L);
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
        feign.FeignException fallo = mock(feign.FeignException.class);
        when(fallo.status()).thenReturn(404);
        when(artistas.obtenerPorId(8L)).thenThrow(fallo);
        var error = assertThrows(ResponseStatusException.class, () -> service.listarFavoritos(1L));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(usuario.getArtistasFavoritos().contains(8L));
        verifyNoInteractions(mongo);
    }

    @Test
    void respuestaIncompletaDelCatalogoDevuelve502() {
        Usuario usuario = new Usuario();
        usuario.getArtistasFavoritos().add(8L);
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
        when(artistas.obtenerPorId(8L)).thenReturn(new com.msvusuario.dto.ArtistaResponse(8L, " ", "Rock"));
        var error = assertThrows(ResponseStatusException.class, () -> service.listarFavoritos(1L));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        verifyNoInteractions(mongo);
    }

    @Test
    void rolEnUsoNoSeElimina() {
        when(roles.findById(1L)).thenReturn(Optional.of(new com.msvusuario.entity.Rol()));
        when(usuarios.existsByRolId(1L)).thenReturn(true);
        RolService rolService = new RolService(roles, usuarios);
        var error = assertThrows(ResponseStatusException.class, () -> rolService.eliminar(1L));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(roles, never()).delete(any());
    }
}
