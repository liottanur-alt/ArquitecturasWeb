package com.msvusuario;

import com.msvusuario.dto.RolRequest;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.repository.UsuarioRepository;
import com.msvusuario.service.RolService;
import com.msvusuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UsuarioPersistenceTests extends MongoIntegrationSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.msvusuario.client.ArtistaClientFeign artistas;
    @Autowired private UsuarioService usuarios;
    @Autowired private RolService roles;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private org.springframework.data.mongodb.core.MongoTemplate mongo;
    @org.junit.jupiter.api.BeforeEach
    void limpiarUsuarios() { usuarioRepository.deleteAll(); }

    @Test
    void variosUsuariosPuedenCompartirRol() {
        Long rol = crearRol();
        Long primero = crearUsuario(rol, "ana@example.com", "11111111");
        Long segundo = crearUsuario(rol, "luis@example.com", "22222222");
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
        usuarios.agregarFavorito(id, 900L);
        var artista = new com.msvusuario.dto.ArtistaResponse(900L, "Soda Stereo", "Rock");
        org.mockito.Mockito.when(artistas.obtenerPorId(900L)).thenReturn(artista);
        assertEquals(java.util.List.of(artista), usuarios.listarFavoritos(id));
    }

    @Test
    void eliminarUsuarioBorraSusFavoritosYConservaLosDeOtros() {
        Long rol = crearRol();
        Long primero = crearUsuario(rol, "ana@example.com", "11111111");
        Long segundo = crearUsuario(rol, "luis@example.com", "22222222");
        usuarios.agregarFavorito(primero, 900L);
        usuarios.agregarFavorito(segundo, 900L);
        usuarios.eliminar(primero);
        assertFalse(usuarioRepository.existsById(primero));
        assertFalse(mongo.exists(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(primero)), com.msvusuario.entity.Usuario.class));
        assertTrue(usuarioRepository.findById(segundo).orElseThrow().getArtistasFavoritos().contains(900L));
        assertNotNull(roles.obtener(rol));
    }

    @Test
    void quitarFavoritoConservaUsuario() {
        Long id = crearUsuario(crearRol(), "ana@example.com", "11111111");
        usuarios.agregarFavorito(id, 900L);
        usuarios.eliminarFavorito(id, 900L);
        assertTrue(usuarioRepository.existsById(id));
        assertTrue(usuarios.listarFavoritos(id).isEmpty());
    }

    private Long crearRol() {
        return roles.listar().stream().filter(r -> r.getName() == TipoRol.CLIENTE).findFirst().orElseThrow().getId();
    }

    @Test
    void edicionDesactualizadaNoSobrescribeUnFavoritoConcurrente() {
        Long id = crearUsuario(crearRol(), "ana@example.com", "11111111");
        var anterior = usuarioRepository.findById(id).orElseThrow();
        usuarios.agregarFavorito(id, 900L);
        anterior.setNombre("Edicion atrasada");
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
                () -> usuarioRepository.save(anterior));
        assertTrue(usuarioRepository.findById(id).orElseThrow().getArtistasFavoritos().contains(900L));
    }

    @Test
    void altasConcurrentesTienenIdsDistintos() throws Exception {
        Long rol = crearRol();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(5)) {
            var tareas = java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<Long>>mapToObj(i ->
                            () -> crearUsuario(rol, "concurrente" + i + "@example.com", "DNI-" + i)).toList();
            var ids = new java.util.HashSet<Long>();
            for (var resultado : executor.invokeAll(tareas)) ids.add(resultado.get());
            assertEquals(20, ids.size());
        }
    }

    @Test
    void favoritosConcurrentesNoSePierdenNiSeDuplican() throws Exception {
        Long id = crearUsuario(crearRol(), "ana@example.com", "11111111");
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(5)) {
            var tareas = java.util.stream.IntStream.range(0, 30)
                    .<java.util.concurrent.Callable<Void>>mapToObj(i -> () -> {
                        usuarios.agregarFavorito(id, (long) (i % 10 + 1));
                        return null;
                    }).toList();
            for (var resultado : executor.invokeAll(tareas)) resultado.get();
        }
        assertEquals(10, usuarioRepository.findById(id).orElseThrow().getArtistasFavoritos().size());
    }

    @Test
    void todosLosTiposDeRolSePersistenPorNombre() {
        for (TipoRol tipo : TipoRol.values()) {
            Long id = roles.listar().stream().filter(r -> r.getName() == tipo).findFirst().orElseThrow().getId();
            assertEquals(tipo, roles.obtener(id).getName());
            var documento = mongo.getCollection("roles").find(new org.bson.Document("_id", id)).first(); assertNotNull(documento); assertEquals(tipo.name(), documento.getString("name"));
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
