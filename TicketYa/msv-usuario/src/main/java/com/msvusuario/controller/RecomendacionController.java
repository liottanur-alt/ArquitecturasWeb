package com.msvusuario.controller;

import com.msvusuario.dto.RecomendacionResponse;
import com.msvusuario.dto.RecomendacionCompraResponse;
import com.msvusuario.dto.RecomendacionesResponse;
import com.msvusuario.dto.ResultadoFuenteRecomendacion;
import com.msvusuario.service.RecomendacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class RecomendacionController {
    private final RecomendacionService recomendaciones;

    @GetMapping("/{id}/recomendaciones")
    public RecomendacionesResponse recomendar(@PathVariable Long id,
            @RequestParam(defaultValue = "10") int limite) {
        return recomendaciones.recomendar(id, limite);
    }

    @GetMapping("/{id}/recomendaciones/por-preferencias")
    public ResponseEntity<List<RecomendacionResponse>> recomendarPorPreferencias(@PathVariable Long id) {
        return respuestaFuente(recomendaciones.recomendarPorPreferencias(id));
    }

    @GetMapping("/{id}/recomendaciones/por-compras")
    public ResponseEntity<List<RecomendacionCompraResponse>> recomendarPorCompras(@PathVariable Long id) {
        return respuestaFuente(recomendaciones.recomendarPorCompras(id));
    }

    private <T> ResponseEntity<List<T>> respuestaFuente(ResultadoFuenteRecomendacion<T> resultado) {
        return ResponseEntity.ok()
                .header("X-Historial-Verificado", Boolean.toString(resultado.historialVerificado()))
                .header("X-Disponibilidad-Verificada", Boolean.toString(resultado.disponibilidadVerificada()))
                .header("X-Respuesta-Parcial", Boolean.toString(!resultado.historialVerificado() || !resultado.disponibilidadVerificada()))
                .body(resultado.recomendaciones());
    }
}
