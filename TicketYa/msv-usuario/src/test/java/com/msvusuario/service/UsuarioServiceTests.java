package com.msvusuario.service;

import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.entity.Usuario;
import com.msvusuario.repository.ArtistaFavoritoRepository;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final ArtistaFavoritoRepository favoritos = mock(ArtistaFavoritoRepository.class);
    private final UsuarioService service = new UsuarioService(usuarios, roles, favoritos);

    @Test
    void eliminarUsuarioEliminaPrimeroSusFavoritos() {
        Usuario usuario = new Usuario();
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
        service.eliminar(1L);
        var orden = inOrder(favoritos, usuarios);
        orden.verify(favoritos).deleteByUsuarioId(1L);
        orden.verify(usuarios).delete(usuario);
    }

    @Test
    void usuarioInexistenteNoPermiteAgregarFavoritos() {
        when(usuarios.findById(1L)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.agregarFavorito(1L, 8L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(favoritos);
    }

    @Test
    void emailInvalidoNoSeGuarda() {
        var request = new UsuarioRequest("Ana", "incorrecto", "12345678", "Tandil", 1L);
        var error = assertThrows(ResponseStatusException.class, () -> service.crear(request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(usuarios, never()).save(any());
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
