package msvevento.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import msvevento.dto.TurnoFilaResponse;
import msvevento.dto.ValidarTurnoResponse;
import msvevento.entity.EstadoEvento;
import msvevento.entity.EstadoPublicacion;
import msvevento.repository.EventoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FilaService {

    private static final long ESPERA_TTL_SEGUNDOS = 86_400;
    private static final String EN_ESPERA = "EN_ESPERA";
    private static final String ADMITIDO = "ADMITIDO";
    private static final String VENCIDO = "VENCIDO";

    private static final DefaultRedisScript<String> REGISTRAR_TURNO = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[4])
            if current then
              local oldKey = ARGV[6] .. current
              if redis.call('EXISTS', oldKey) == 1 then
                local state = redis.call('HGET', oldKey, 'estado')
                local expiresAt = tonumber(redis.call('HGET', oldKey, 'venceEn') or '0')
                if state == 'EN_ESPERA' or (state == 'ADMITIDO' and expiresAt > tonumber(ARGV[1])) then
                  return current
                end
                redis.call('ZREM', KEYS[1], current)
                redis.call('ZREM', KEYS[2], current)
                redis.call('DEL', oldKey)
              end
              redis.call('DEL', KEYS[4])
            end
            redis.call('HSET', KEYS[5], 'turnoId', ARGV[4], 'clienteId', ARGV[3],
              'estado', 'EN_ESPERA', 'autorizacion', ARGV[5], 'venceEn', '0', 'idOperacion', '')
            local sequence = redis.call('INCR', KEYS[3])
            redis.call('ZADD', KEYS[1], sequence, ARGV[4])
            redis.call('SET', KEYS[4], ARGV[4], 'EX', tonumber(ARGV[2]))
            redis.call('EXPIRE', KEYS[5], tonumber(ARGV[2]))
            return ARGV[4]
            """, String.class);

    private static final DefaultRedisScript<Long> ADMITIR_ESPERA = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now)
            for _, turnId in ipairs(expired) do
              local turnKey = ARGV[4] .. turnId
              if redis.call('EXISTS', turnKey) == 1 then
                redis.call('HSET', turnKey, 'estado', 'VENCIDO')
              end
              redis.call('ZREM', KEYS[2], turnId)
            end
            local active = redis.call('ZCARD', KEYS[2])
            local promoted = 0
            while active < tonumber(ARGV[2]) do
              local first = redis.call('ZRANGE', KEYS[1], 0, 0)
              if #first == 0 then break end
              local turnId = first[1]
              redis.call('ZREM', KEYS[1], turnId)
              local turnKey = ARGV[4] .. turnId
              if redis.call('EXISTS', turnKey) == 1 and redis.call('HGET', turnKey, 'estado') == 'EN_ESPERA' then
                local clientId = redis.call('HGET', turnKey, 'clienteId')
                local expiresAt = now + tonumber(ARGV[3])
                redis.call('HSET', turnKey, 'estado', 'ADMITIDO', 'venceEn', tostring(expiresAt))
                redis.call('PEXPIRE', turnKey, tonumber(ARGV[6]))
                redis.call('SET', ARGV[5] .. clientId, turnId, 'EX', tonumber(ARGV[7]))
                redis.call('ZADD', KEYS[2], expiresAt, turnId)
                active = active + 1
                promoted = promoted + 1
              end
            end
            return promoted
            """, Long.class);

    private static final DefaultRedisScript<String> VALIDAR_AUTORIZACION = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 'TURNO_INEXISTENTE' end
            if redis.call('HGET', KEYS[1], 'autorizacion') ~= ARGV[2] then return 'AUTORIZACION_INVALIDA' end
            if redis.call('HGET', KEYS[1], 'estado') ~= 'ADMITIDO' then return 'TURNO_NO_ADMITIDO' end
            local expiresAt = tonumber(redis.call('HGET', KEYS[1], 'venceEn') or '0')
            if expiresAt <= tonumber(ARGV[1]) then
              redis.call('HSET', KEYS[1], 'estado', 'VENCIDO')
              redis.call('ZREM', KEYS[2], ARGV[4])
              return 'TURNO_VENCIDO'
            end
            local usedBy = redis.call('HGET', KEYS[1], 'idOperacion') or ''
            if usedBy ~= '' and usedBy ~= ARGV[3] then return 'TURNO_YA_USADO' end
            redis.call('HSET', KEYS[1], 'idOperacion', ARGV[3])
            return 'OK'
            """, String.class);

    private static final DefaultRedisScript<String> COMPLETAR_COMPRA = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 'TURNO_INEXISTENTE' end
            if redis.call('HGET', KEYS[1], 'autorizacion') ~= ARGV[2] then return 'AUTORIZACION_INVALIDA' end
            local operation = redis.call('HGET', KEYS[1], 'idOperacion') or ''
            if operation ~= ARGV[3] then return 'OPERACION_DISTINTA' end
            local state = redis.call('HGET', KEYS[1], 'estado')
            if state == 'COMPLETADO' then return 'OK' end
            if state ~= 'ADMITIDO' then return 'TURNO_NO_ADMITIDO' end
            redis.call('HSET', KEYS[1], 'estado', 'COMPLETADO')
            redis.call('ZREM', KEYS[2], ARGV[4])
            return 'OK'
            """, String.class);

    private final EventoRepository eventoRepository;
    private final StringRedisTemplate redis;
    private final int maxCompradoresSimultaneos;
    private final long duracionTurnoMillis;

    public FilaService(
            EventoRepository eventoRepository,
            StringRedisTemplate redis,
            @Value("${fila.max-compradores-simultaneos:10}") int maxCompradoresSimultaneos,
            @Value("${fila.duracion-turno-segundos:900}") long duracionTurnoSegundos
    ) {
        this.eventoRepository = eventoRepository;
        this.redis = redis;
        if (maxCompradoresSimultaneos <= 0 || duracionTurnoSegundos <= 0) {
            throw new IllegalArgumentException("El límite simultáneo y la duración del turno deben ser positivos");
        }
        this.maxCompradoresSimultaneos = maxCompradoresSimultaneos;
        this.duracionTurnoMillis = duracionTurnoSegundos * 1_000;
    }

    public TurnoFilaResponse entrar(Long eventoId, Long clienteId) {
        validarEventoConFila(eventoId);
        String keysBase = "fila:evento:" + eventoId + ":";
        String turnId = UUID.randomUUID().toString();
        String authorization = UUID.randomUUID().toString();
        redis.execute(REGISTRAR_TURNO, List.of(
                        keysBase + "espera", keysBase + "activos", keysBase + "secuencia",
                        keysBase + "cliente:" + clienteId, keysBase + "turno:" + turnId),
                Long.toString(System.currentTimeMillis()), Long.toString(ESPERA_TTL_SEGUNDOS),
                clienteId.toString(), turnId, authorization, keysBase + "turno:");
        promover(eventoId);
        return consultar(eventoId, clienteId, false);
    }

    public TurnoFilaResponse consultar(Long eventoId, Long clienteId) {
        return consultar(eventoId, clienteId, true);
    }

    private TurnoFilaResponse consultar(Long eventoId, Long clienteId, boolean validarEvento) {
        if (validarEvento) validarEventoConFila(eventoId);
        promover(eventoId);
        String base = "fila:evento:" + eventoId + ":";
        String turnId = redis.opsForValue().get(base + "cliente:" + clienteId);
        if (turnId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenés un turno para este evento");
        }
        String turnKey = base + "turno:" + turnId;
        Map<Object, Object> data = redis.opsForHash().entries(turnKey);
        String state = (String) data.get("estado");
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El turno ya no existe; volvé a entrar a la fila");
        }
        Long position = null;
        if (EN_ESPERA.equals(state)) {
            Long rank = redis.opsForZSet().rank(base + "espera", turnId);
            position = rank == null ? null : rank + 1;
        }
        String authorization = ADMITIDO.equals(state) ? (String) data.get("autorizacion") : null;
        String expires = (String) data.get("venceEn");
        Instant expiresAt = expires == null || "0".equals(expires) ? null : Instant.ofEpochMilli(Long.parseLong(expires));
        return new TurnoFilaResponse(eventoId, turnId, state, position, authorization, expiresAt);
    }

    public ValidarTurnoResponse validarAutorizacion(Long eventoId, Long clienteId, String authorization, String operationId) {
        var evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado"));
        if (!evento.isDemanda()) return new ValidarTurnoResponse(true, false, "FILA_NO_REQUERIDA");
        if (authorization == null || authorization.isBlank() || operationId == null || operationId.isBlank()) {
            return new ValidarTurnoResponse(false, true, "AUTORIZACION_Y_OPERACION_REQUERIDAS");
        }

        String base = "fila:evento:" + eventoId + ":";
        String turnId = redis.opsForValue().get(base + "cliente:" + clienteId);
        if (turnId == null) return new ValidarTurnoResponse(false, true, "TURNO_AJENO_O_INEXISTENTE");
        String result = redis.execute(VALIDAR_AUTORIZACION,
                List.of(base + "turno:" + turnId, base + "activos"),
                Long.toString(System.currentTimeMillis()), authorization == null ? "" : authorization,
                operationId == null ? "" : operationId, turnId);
        boolean allowed = "OK".equals(result);
        return new ValidarTurnoResponse(allowed, true, allowed ? "AUTORIZADO" : result);
    }

    public ValidarTurnoResponse completarCompra(Long eventoId, Long clienteId, String authorization, String operationId) {
        String base = "fila:evento:" + eventoId + ":";
        String turnId = redis.opsForValue().get(base + "cliente:" + clienteId);
        if (turnId == null) return new ValidarTurnoResponse(false, true, "TURNO_AJENO_O_INEXISTENTE");
        String result = redis.execute(COMPLETAR_COMPRA,
                List.of(base + "turno:" + turnId, base + "activos"),
                Long.toString(System.currentTimeMillis()), authorization == null ? "" : authorization,
                operationId == null ? "" : operationId, turnId);
        if ("OK".equals(result)) promover(eventoId);
        boolean completed = "OK".equals(result);
        return new ValidarTurnoResponse(completed, true, completed ? "COMPRA_COMPLETADA" : result);
    }

    private void validarEventoConFila(Long eventoId) {
        var evento = eventoRepository.findByIdAndEstadoPublicacion(eventoId, EstadoPublicacion.PUBLICADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento publicado no encontrado"));
        if (!evento.isDemanda()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este evento no requiere fila virtual");
        }
        if (evento.getEstado() == EstadoEvento.CANCELADO || evento.getEstado() == EstadoEvento.FINALIZADO
                || evento.getEstado() == EstadoEvento.AGOTADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El evento no admite nuevos turnos de compra");
        }
    }

    private void promover(Long eventoId) {
        String base = "fila:evento:" + eventoId + ":";
        redis.execute(ADMITIR_ESPERA, List.of(base + "espera", base + "activos"),
                Long.toString(System.currentTimeMillis()), Integer.toString(maxCompradoresSimultaneos),
                Long.toString(duracionTurnoMillis), base + "turno:", base + "cliente:",
                Long.toString(ESPERA_TTL_SEGUNDOS * 1_000), Long.toString(ESPERA_TTL_SEGUNDOS));
    }
}
