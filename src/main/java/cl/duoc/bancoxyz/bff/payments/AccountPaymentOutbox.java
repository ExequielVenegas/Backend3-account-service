package cl.duoc.bancoxyz.bff.payments;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @ConditionalOnProperty(name="payments.kafka.enabled",havingValue="true")
public class AccountPaymentOutbox {
 private final JdbcTemplate db; private final KafkaTemplate<String,String> kafka; private final String topic;
 public AccountPaymentOutbox(JdbcTemplate db,@Qualifier("paymentKafkaTemplate") KafkaTemplate<String,String> kafka,
  @Value("${payments.kafka.results-topic:bancoxyz.pagos.results.v1}") String topic) { this.db=db; this.kafka=kafka; this.topic=topic; }
 @Transactional(rollbackFor=Exception.class) public boolean publishOne() throws Exception {
  var rows=db.queryForList("SELECT event_id,payload FROM account_payment_outbox WHERE published_at IS NULL ORDER BY created_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED");
  if(rows.isEmpty()) return false;
  var row=rows.getFirst();
  kafka.send(topic,(String)row.get("event_id"),(String)row.get("payload")).get(8,TimeUnit.SECONDS);
  // Solo marcar enviado despues del ACK. Un crash antes del COMMIT produce una repeticion segura.
  db.update("UPDATE account_payment_outbox SET published_at=CURRENT_TIMESTAMP(6) WHERE event_id=?",row.get("event_id"));
  return true;
 }
}
