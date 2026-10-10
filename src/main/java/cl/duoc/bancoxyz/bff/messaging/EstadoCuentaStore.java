package cl.duoc.bancoxyz.bff.messaging;
import java.util.*;
import cl.duoc.bancoxyz.bff.common.exception.ResourceNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@ConditionalOnProperty(name="lab.kafka.enabled", havingValue="true")
public class EstadoCuentaStore {
    private final JdbcTemplate db;
    public EstadoCuentaStore(JdbcTemplate db) { this.db=db; }
    @Transactional
    public boolean save(EstadoCuentaCommand command) {
        if (db.queryForObject("SELECT COUNT(*) FROM calculo_intereses WHERE cuenta_id=?", Integer.class, command.cuentaId()) == 0)
            throw new IllegalArgumentException("Cuenta inexistente");
        try {
            db.update("INSERT INTO solicitudes_estado_cuenta (solicitud_id,cliente,cuenta_id,anio,estado) VALUES (?,?,?,?,?)",
                    command.solicitudId().toString(),command.cliente(),command.cuentaId(),command.anio(),"REGISTRADA");
            return true;
        } catch (DuplicateKeyException duplicate) {
            // Lectura con bloqueo ve el COMMIT ganador incluso bajo REPEATABLE READ de MySQL.
            var old = db.queryForMap("SELECT cliente,cuenta_id,anio FROM solicitudes_estado_cuenta WHERE solicitud_id=? FOR UPDATE",
                    command.solicitudId().toString());
            if (!command.cliente().equals(old.get("cliente")) ||
                    command.cuentaId().intValue() != ((Number)old.get("cuenta_id")).intValue() ||
                    command.anio().intValue() != ((Number)old.get("anio")).intValue())
                throw new IllegalArgumentException("Idempotency-Key reutilizada con otro contenido");
            return false;
        }
    }
    public Map<String,Object> find(UUID id, String cliente) {
        var rows = db.queryForList("SELECT solicitud_id,cuenta_id,anio,estado,recibida_en FROM solicitudes_estado_cuenta WHERE solicitud_id=? AND cliente=?",
                id.toString(), cliente);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Solicitud no disponible");
        var row = rows.getFirst();
        return Map.of("solicitudId",row.get("solicitud_id"),"cuentaId",row.get("cuenta_id"),
                "anio",row.get("anio"),"estado",row.get("estado"),"recibidaEn",row.get("recibida_en").toString());
    }
}
