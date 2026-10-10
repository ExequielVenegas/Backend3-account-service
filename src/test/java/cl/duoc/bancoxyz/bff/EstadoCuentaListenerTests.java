package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.messaging.*;
import jakarta.validation.Validation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EstadoCuentaListenerTests {
    @Test void rejectsMalformedNullUnsupportedVersionAndWrongKeyBeforeDatabase() {
        var store=mock(EstadoCuentaStore.class);
        try(var validators=Validation.buildDefaultValidatorFactory()) {
            var json=JsonMapper.builder().build();
            var listener=new EstadoCuentaListener(json,validators.getValidator(),store);
            for(String payload:new String[]{"broken","null","{}",json.writeValueAsString(new EstadoCuentaCommand(UUID.randomUUID(),2,"SOLICITAR_ESTADO_CUENTA","web-demo",137,2026))}) {
                assertThrows(IllegalArgumentException.class,()->listener.receive(new ConsumerRecord<>("test",0,0,"invalid",payload)));
            }
            var valid=new EstadoCuentaCommand(UUID.randomUUID(),1,"SOLICITAR_ESTADO_CUENTA","web-demo",137,2026);
            assertThrows(IllegalArgumentException.class,()->listener.receive(new ConsumerRecord<>("test",0,0,"wrong-key",json.writeValueAsString(valid))));
            verifyNoInteractions(store);
            listener.receive(new ConsumerRecord<>("test",0,0,valid.solicitudId().toString(),json.writeValueAsString(valid)));
            verify(store).save(valid);
        }
    }
}
