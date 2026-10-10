package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.payments.*;
import cl.duoc.bancoxyz.contract.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:account_outbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "lab.kafka.enabled=true","lab.kafka.listener-auto-startup=false","payments.kafka.enabled=true",
 "payments.kafka.listener-auto-startup=false","payments.outbox.initial-delay-ms=3600000"})
class AccountPaymentOutboxTests {
 @Autowired JdbcTemplate db; @Autowired PaymentLedger ledger; @Autowired AccountPaymentOutbox outbox;
 @MockitoBean(name="paymentKafkaTemplate") KafkaTemplate<String,String> kafka;
 @Test void resultSurvivesOfflineBrokerAndPublishesAfterAck() throws Exception {
  UUID account=UUID.randomUUID();
  db.update("INSERT INTO modern_accounts(account_id,customer_id,creation_hash,created_by,tipo,alias) VALUES(?,?,?,?,?,?)",account.toString(),UUID.randomUUID().toString(),"0".repeat(64),"test","AHORRO","Demo");
  ledger.apply(new PaymentCommand(1,UUID.randomUUID(),"web-demo","DEPOSITO",null,account,new BigDecimal("10"),"Demo"));
  when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
  assertThrows(Exception.class,()->outbox.publishOne());
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_payment_outbox WHERE published_at IS NULL",Integer.class));
  assertEquals(new BigDecimal("10.00"),db.queryForObject("SELECT saldo FROM modern_accounts WHERE account_id=?",BigDecimal.class,account.toString()));
  when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(null));
  assertTrue(outbox.publishOne()); assertFalse(outbox.publishOne());
 }
}
