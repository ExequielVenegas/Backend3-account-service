package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.payments.*;
import cl.duoc.bancoxyz.contract.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:ledger;MODE=MySQL;DB_CLOSE_DELAY=-1")
class PaymentLedgerTests {
 @Autowired PaymentLedger ledger; @Autowired JdbcTemplate db; @Autowired PlatformTransactionManager transactions;
 final UUID a=UUID.fromString("a0000000-0000-0000-0000-000000000001"),b=UUID.fromString("b0000000-0000-0000-0000-000000000002"),c=UUID.fromString("c0000000-0000-0000-0000-000000000003");
 @BeforeEach void setup() {
  db.update("DELETE FROM account_payment_ledger"); db.update("DELETE FROM account_payment_outbox"); db.update("DELETE FROM account_payment_operations");
  db.update("DELETE FROM modern_account_audit"); db.update("DELETE FROM modern_accounts");
  for(UUID id:List.of(a,b,c)) db.update("INSERT INTO modern_accounts(account_id,customer_id,creation_hash,created_by,tipo,alias) VALUES(?,?,?,?,?,?)",id.toString(),UUID.randomUUID().toString(),"0".repeat(64),"test","AHORRO","Demo");
 }
 PaymentCommand command(String type,UUID from,UUID to,String amount) { return new PaymentCommand(1,UUID.randomUUID(),"web-demo",type,from,to,new BigDecimal(amount),"Demo"); }
 BigDecimal balance(UUID id) { return db.queryForObject("SELECT saldo FROM modern_accounts WHERE account_id=?",BigDecimal.class,id.toString()); }
 int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
 @Test void depositAndTransferProduceBalancedEntriesAndResults() {
  assertEquals("APLICADO",ledger.apply(command("DEPOSITO",null,a,"100")).estado());
  var transfer=command("TRANSFERENCIA",a,b,"30"); assertEquals("APLICADO",ledger.apply(transfer).estado());
  assertEquals(new BigDecimal("70.00"),balance(a)); assertEquals(new BigDecimal("30.00"),balance(b));
  assertEquals(BigDecimal.ZERO.setScale(2),db.queryForObject("SELECT SUM(monto) FROM account_payment_ledger WHERE payment_id=?",BigDecimal.class,transfer.pagoId().toString()));
  assertEquals(2,count("account_payment_outbox")); assertEquals(3,count("account_payment_ledger"));
 }
 @Test void duplicateNeverMovesMoneyTwice() {
  var deposit=command("DEPOSITO",null,a,"100"); var first=ledger.apply(deposit); assertEquals(first,ledger.apply(deposit));
  assertEquals(new BigDecimal("100.00"),balance(a)); assertEquals(1,count("account_payment_ledger")); assertEquals(1,count("account_payment_outbox"));
  assertThrows(IllegalArgumentException.class,()->ledger.apply(new PaymentCommand(1,deposit.pagoId(),"web-demo","DEPOSITO",null,a,BigDecimal.ONE,"Demo")));
 }
 @Test void insufficientFundsRejectsWithoutPartialCredit() {
  var result=ledger.apply(command("TRANSFERENCIA",a,b,"20")); assertEquals("SALDO_INSUFICIENTE",result.motivo());
  assertEquals(0,balance(a).signum()); assertEquals(0,balance(b).signum()); assertEquals(0,count("account_payment_ledger")); assertEquals(1,count("account_payment_outbox"));
 }
 @Test void blockedClosedAndMissingAccountsAreRejected() {
  db.update("UPDATE modern_accounts SET estado='BLOQUEADA' WHERE account_id=?",a.toString());
  assertEquals("CUENTA_NO_ACTIVA",ledger.apply(command("DEPOSITO",null,a,"20")).motivo());
  db.update("UPDATE modern_accounts SET estado='CERRADA' WHERE account_id=?",a.toString());
  assertEquals("CUENTA_NO_ACTIVA",ledger.apply(command("DEPOSITO",null,a,"20")).motivo());
  assertEquals("CUENTA_NO_EXISTE",ledger.apply(command("DEPOSITO",null,UUID.randomUUID(),"20")).motivo());
  assertEquals(0,count("account_payment_ledger"));
 }
 @Test void unexpectedFailureRollsBackBalancesLedgerAndOutbox() {
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{ledger.apply(command("DEPOSITO",null,a,"100"));throw new IllegalStateException("crash");}));
  assertEquals(0,balance(a).signum()); assertEquals(0,count("account_payment_ledger")); assertEquals(0,count("account_payment_operations")); assertEquals(0,count("account_payment_outbox"));
 }
 @Test void concurrentSpendingCannotOverdraw() throws Exception {
  ledger.apply(command("DEPOSITO",null,a,"100"));
  try(var pool=Executors.newFixedThreadPool(2)) {
   List<Callable<PaymentResult>> tasks=List.of(()->ledger.apply(command("TRANSFERENCIA",a,b,"80")),()->ledger.apply(command("TRANSFERENCIA",a,c,"80")));
   int applied=0; for(var task:pool.invokeAll(tasks)) if(task.get().estado().equals("APLICADO")) applied++;
   assertEquals(1,applied);
  }
  assertEquals(new BigDecimal("20.00"),balance(a)); assertEquals(new BigDecimal("80.00"),balance(b).add(balance(c)));
 }
 @Test void concurrentDuplicateDeliveryAppliesOnce() throws Exception {
  var command=command("DEPOSITO",null,a,"100");
  try(var pool=Executors.newFixedThreadPool(3)) {
   List<Callable<PaymentResult>> tasks=List.of(()->ledger.apply(command),()->ledger.apply(command),()->ledger.apply(command));
   for(var task:pool.invokeAll(tasks)) assertEquals("APLICADO",task.get().estado());
  }
  assertEquals(new BigDecimal("100.00"),balance(a)); assertEquals(1,count("account_payment_ledger"));
 }
 @Test void oppositeTransfersUseStableLockOrderAndPreserveTotal() throws Exception {
  ledger.apply(command("DEPOSITO",null,a,"100")); ledger.apply(command("DEPOSITO",null,b,"100"));
  try(var pool=Executors.newFixedThreadPool(2)) {
   List<Callable<PaymentResult>> tasks=List.of(()->ledger.apply(command("TRANSFERENCIA",a,b,"20")),()->ledger.apply(command("TRANSFERENCIA",b,a,"10")));
   for(var task:pool.invokeAll(tasks)) assertEquals("APLICADO",task.get().estado());
  }
  assertEquals(new BigDecimal("90.00"),balance(a)); assertEquals(new BigDecimal("110.00"),balance(b));
 }
 @Test void malformedContractIsNotAcceptedAsBusinessRejection() {
  assertThrows(IllegalArgumentException.class,()->ledger.apply(command("DEPOSITO",null,a,"0")));
  assertThrows(IllegalArgumentException.class,()->ledger.apply(command("TRANSFERENCIA",a,a,"10")));
  assertEquals(0,count("account_payment_operations"));
 }
 @Test void withdrawalDebitsOnlyOnceAndCannotOverdraw() {
  ledger.apply(command("DEPOSITO",null,a,"100"));
  var withdrawal=command("RETIRO",a,null,"25");
  assertEquals("APLICADO",ledger.apply(withdrawal).estado());
  assertEquals("APLICADO",ledger.apply(withdrawal).estado());
  assertEquals(new BigDecimal("75.00"),balance(a));
  assertEquals("SALDO_INSUFICIENTE",ledger.apply(command("RETIRO",a,null,"100")).motivo());
  assertEquals(new BigDecimal("75.00"),balance(a));
  assertEquals(2,count("account_payment_ledger"));
 }
 @Test void paymentToBeneficiaryUsesSameAtomicRules() {
  ledger.apply(command("DEPOSITO",null,a,"100")); assertEquals("APLICADO",ledger.apply(command("PAGO",a,b,"25")).estado());
  assertEquals(new BigDecimal("75.00"),balance(a)); assertEquals(new BigDecimal("25.00"),balance(b));
 }
}
