package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.messaging.*;
import cl.duoc.bancoxyz.bff.common.exception.ResourceNotFoundException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"lab.kafka.enabled=true","lab.kafka.listener-auto-startup=false",
        "spring.datasource.url=jdbc:h2:mem:kafka_test;MODE=MySQL;DB_CLOSE_DELAY=-1"})
class EstadoCuentaStoreTests {
    @Autowired EstadoCuentaStore store;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void setup() {
        db.execute("CREATE TABLE IF NOT EXISTS calculo_intereses(cuenta_id INT PRIMARY KEY)");
        db.update("MERGE INTO calculo_intereses KEY(cuenta_id) VALUES(137)");
        db.update("DELETE FROM solicitudes_estado_cuenta");
    }
    EstadoCuentaCommand command(UUID id,int year) { return new EstadoCuentaCommand(id,1,"SOLICITAR_ESTADO_CUENTA","web-demo",137,year); }
    @Test void redeliveryLeavesExactlyOneRowAndOriginalTimestamp() {
        var command=command(UUID.randomUUID(),2026);
        assertTrue(store.save(command));
        var first=store.find(command.solicitudId(),"web-demo");
        assertFalse(store.save(command));
        assertEquals(first,store.find(command.solicitudId(),"web-demo"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM solicitudes_estado_cuenta",Integer.class));
    }
    @Test void conflictingReuseCannotOverwriteOriginal() {
        UUID id=UUID.randomUUID(); store.save(command(id,2026));
        assertThrows(IllegalArgumentException.class,()->store.save(command(id,2027)));
        assertEquals(2026,store.find(id,"web-demo").get("anio"));
    }
    @Test void concurrentDuplicatesOnlyInsertOnce() throws Exception {
        var command=command(UUID.randomUUID(),2026);
        try (var pool=Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> tasks=List.of(()->store.save(command),()->store.save(command),()->store.save(command),()->store.save(command));
            int created=0;
            for (var result:pool.invokeAll(tasks)) if(result.get()) created++;
            assertEquals(1,created);
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM solicitudes_estado_cuenta",Integer.class));
    }
    @Test void failedTransactionRollsBackAndCanBeRedelivered() {
        var command=command(UUID.randomUUID(),2026);
        assertThrows(IllegalStateException.class,()-> new TransactionTemplate(transactions).execute(status->{
            store.save(command); throw new IllegalStateException("simulated failure before commit");
        }));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM solicitudes_estado_cuenta",Integer.class));
        assertTrue(store.save(command));
    }
    @Test void unknownAccountCannotBeInserted() {
        var invalid=new EstadoCuentaCommand(UUID.randomUUID(),1,"SOLICITAR_ESTADO_CUENTA","web-demo",999,2026);
        assertThrows(IllegalArgumentException.class,()->store.save(invalid));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM solicitudes_estado_cuenta",Integer.class));
    }
    @Test void anotherClientCannotReadRequest() {
        var command=command(UUID.randomUUID(),2026); store.save(command);
        assertThrows(ResourceNotFoundException.class,()->store.find(command.solicitudId(),"otro"));
    }
}
