package cl.duoc.bancoxyz.bff.messaging;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="lab.kafka.enabled", havingValue="true")
public class SolicitudQueryController {
    private final EstadoCuentaStore store;
    public SolicitudQueryController(EstadoCuentaStore store) { this.store=store; }
    @GetMapping("/internal/solicitudes-estado/{id}")
    public Map<String,Object> find(@PathVariable UUID id,@RequestParam String cliente) { return store.find(id,cliente); }
}
