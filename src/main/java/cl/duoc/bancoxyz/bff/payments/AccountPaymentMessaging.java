package cl.duoc.bancoxyz.bff.payments;
import cl.duoc.bancoxyz.contract.PaymentCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component @ConditionalOnProperty(name="payments.kafka.enabled",havingValue="true")
public class AccountPaymentMessaging {
 private final AccountPaymentOutbox outbox; private final PaymentLedger ledger; private final ObjectMapper json;
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AccountPaymentMessaging.class);
 public AccountPaymentMessaging(AccountPaymentOutbox outbox,PaymentLedger ledger,ObjectMapper json) { this.outbox=outbox; this.ledger=ledger; this.json=json; }
 @Scheduled(initialDelayString="${payments.outbox.initial-delay-ms:5000}",fixedDelayString="${payments.outbox.delay-ms:5000}") public void sendPending() {
  try { for(int i=0;i<10 && outbox.publishOne();i++) {} }
  catch(InterruptedException e) { Thread.currentThread().interrupt(); }
  catch(Exception e) { log.warn("Resultado de pago pendiente de envio; se conserva en outbox"); }
 }
 @KafkaListener(topics="${payments.kafka.commands-topic:bancoxyz.pagos.commands.v1}",groupId="account-payments",containerFactory="paymentListenerFactory",autoStartup="${payments.kafka.listener-auto-startup:true}")
 public void command(String payload) {
  final PaymentCommand command;
  try { command=json.readValue(payload,PaymentCommand.class); }
  catch(RuntimeException e) { throw new IllegalArgumentException("JSON de pago invalido",e); }
  if(command==null) throw new IllegalArgumentException("Comando nulo");
  ledger.apply(command);
 }
}
