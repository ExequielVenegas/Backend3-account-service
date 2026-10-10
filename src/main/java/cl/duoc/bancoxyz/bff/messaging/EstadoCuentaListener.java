package cl.duoc.bancoxyz.bff.messaging;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component @DependsOn("solicitudSchema")
@ConditionalOnProperty(name="lab.kafka.enabled", havingValue="true")
public class EstadoCuentaListener {
    private static final Logger log=LoggerFactory.getLogger(EstadoCuentaListener.class);
    private final ObjectMapper json;
    private final Validator validator;
    private final EstadoCuentaStore store;
    public EstadoCuentaListener(ObjectMapper json, Validator validator, EstadoCuentaStore store) {
        this.json=json; this.validator=validator; this.store=store;
    }
    @KafkaListener(topics="${lab.kafka.topic:bancoxyz.cuentas.commands.v1}", groupId="account-service",
            containerFactory="commandListenerFactory", autoStartup="${lab.kafka.listener-auto-startup:true}")
    public void receive(ConsumerRecord<String,String> record) {
        final EstadoCuentaCommand command;
        try { command=json.readValue(record.value(),EstadoCuentaCommand.class); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("JSON de comando invalido"); }
        if (command == null || !validator.validate(command).isEmpty() || !command.solicitudId().toString().equals(record.key()))
            throw new IllegalArgumentException("Contrato de comando invalido");
        boolean created=store.save(command);
        log.info("Solicitud {} {}; Kafka particion={} offset={}", command.solicitudId(),
                created ? "REGISTRADA" : "DUPLICADA_IGNORADA", record.partition(),record.offset());
    }
}
