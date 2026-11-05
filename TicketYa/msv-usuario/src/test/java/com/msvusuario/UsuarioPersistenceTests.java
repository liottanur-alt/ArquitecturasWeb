package com.msvusuario;

import com.msvusuario.dto.RolRequest;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.entity.ArtistaFavoritoID;
import com.msvusuario.repository.ArtistaFavoritoRepository;
import com.msvusuario.repository.UsuarioRepository;
import com.msvusuario.service.RolService;
import com.msvusuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuarioPersistenceTests {
    @Autowired private UsuarioService usuarios;
    @Autowired private RolService roles;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ArtistaFavoritoRepository favoritos;
    @Autowired private EntityManager entityManager;

    @Test
    void variosUsuariosPuedenCompartirRol() {
        Long rol = crearRol();
        Long primero = crearUsuario(rol, "ana@example.com", "11111111");
        Long segundo = crearUsuario(rol, "luis@example.com", "22222222");
        entityManager.flush();
        entityManager.clear();
        assertEquals(rol, usuarios.obtener(primero).idRol());
        assertEquals(rol, usuarios.obtener(segundo).idRol());
    }

    @Test
    void emailDuplicadoEsRechazadoPorLaBase() {
        Long rol = crearRol();
        crearUsuario(rol, "ana@example.com", "11111111");
        assertThrows(DataIntegrityViolationException.class,
                () -> crearUsuario(rol, "ana@example.com", "22222222"));
    }

    @Test
    void dniDuplicadoEsRechazadoPorLaBase() {
        Long rol = crearRol();
        crearUsuario(rol, "ana@example.com", "11111111");
        assertThrows(DataIntegrityViolationException.class,
                () -> crearUsuario(rol, "luis@example.com", "11111111"));
    }

    @Test
    void favoritoExternoSePersisteSinTablaArtistaYAgregarloDosVecesNoLoDuplica() {
        Long id = crearUsuario(crearRol(), "ana@example.com", "11111111");
        usuarios.agregarFavorito(id, 900L);
        entityManager.flush();
        entityManager.clear();
        usuarios.agregarFavorito(id, 900L);
        entityManager.flush();
        entityManager.clear();
        assertEquals(java.util.List.of(900L), usuarios.listarFavoritos(id));
    }

    @Test
    void eliminarUsuarioBorraSusFavoritosYConservaLosDeOtros() {
        Long rol = crearRol();
        Long primero = crearUsuario(rol, "ana@example.com", "11111111");
        Long segundo = crearUsuario(rol, "luis@example.com", "22222222");
        usuarios.agregarFavorito(primero, 900L);
        usuarios.agregarFavorito(segundo, 900L);
        entityManager.flush();
        entityManager.clear();
        usuarios.eliminar(primero);
        entityManager.flush();
        entityManager.clear();
        assertFalse(usuarioRepository.existsById(primero));
        assertFalse(favoritos.existsById(new ArtistaFavoritoID(primero, 900L)));
        assertTrue(favoritos.existsById(new ArtistaFavoritoID(segundo, 900L)));
        assertNotNull(roles.obtener(rol));
    }

    @Test
    void quitarFavoritoConservaUsuario() {
        Long id = crearUsuario(crearRol(), "ana@example.com", "11111111");
        usuarios.agregarFavorito(id, 900L);
        entityManager.flush();
        usuarios.eliminarFavorito(id, 900L);
        entityManager.flush();
        entityManager.clear();
        assertTrue(usuarioRepository.existsById(id));
        assertTrue(usuarios.listarFavoritos(id).isEmpty());
    }

    private Long crearRol() {
        return roles.crear(new RolRequest(TipoRol.CLIENTE)).getId();
    }

    @Test
    void todosLosTiposDeRolSePersistenPorNombre() {
        for (TipoRol tipo : TipoRol.values()) {
            Long id = roles.crear(new RolRequest(tipo)).getId();
            entityManager.flush();
            entityManager.clear();
            assertEquals(tipo, roles.obtener(id).getName());
            Object valor = entityManager.createNativeQuery("SELECT name FROM roles WHERE id = :id")
                    .setParameter("id", id).getSingleResult();
            assertEquals(tipo.name(), valor.toString());
        }
    }

    @Test
    void rolSinTipoEsRechazado() {
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> roles.crear(new RolRequest(null)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private Long crearUsuario(Long rol, String email, String dni) {
        return usuarios.crear(new UsuarioRequest("Ana", email, dni, "Tandil", rol)).id();
    }
}
