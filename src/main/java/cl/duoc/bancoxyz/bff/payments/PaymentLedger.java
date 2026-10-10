package cl.duoc.bancoxyz.bff.payments;

import cl.duoc.bancoxyz.contract.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentLedger {
 private final JdbcTemplate db; private final ObjectMapper json;
 private static final BigDecimal MAX_BALANCE=new BigDecimal("99999999999999999.99");
 public PaymentLedger(JdbcTemplate db,ObjectMapper json) { this.db=db; this.json=json; }
 @Transactional public PaymentResult apply(PaymentCommand raw) {
  var command=raw.normalized(); String id=command.pagoId().toString(),hash=command.fingerprint(json);
  try { db.update("INSERT INTO account_payment_operations(payment_id,command_hash) VALUES(?,?)",id,hash); }
  catch(DuplicateKeyException duplicate) {
   var row=db.queryForMap("SELECT command_hash,result_payload FROM account_payment_operations WHERE payment_id=? FOR UPDATE",id);
   if(!hash.equals(row.get("command_hash"))) throw new IllegalArgumentException("Pago repetido con contenido distinto");
   return json.readValue((String)row.get("result_payload"),PaymentResult.class);
  }
  // Mismo orden de bloqueo para transferencias opuestas y operaciones concurrentes.
  var ids=new TreeSet<String>(); if(command.destino()!=null) ids.add(command.destino().toString());
  if(command.origen()!=null) ids.add(command.origen().toString());
  Map<String,Map<String,Object>> accounts=new HashMap<>();
  for(var account:ids) {
   var rows=db.queryForList("SELECT account_id,saldo,estado,moneda,version FROM modern_accounts WHERE account_id=? FOR UPDATE",account);
   if(!rows.isEmpty()) accounts.put(account,rows.getFirst());
  }
  String reason=null;
  if(accounts.size()!=ids.size()) reason="CUENTA_NO_EXISTE";
  else if(accounts.values().stream().anyMatch(a->!"ACTIVA".equals(a.get("estado")))) reason="CUENTA_NO_ACTIVA";
  else if(accounts.values().stream().anyMatch(a->!"CLP".equals(a.get("moneda")))) reason="MONEDA_NO_ADMITIDA";
  else if(command.origen()!=null && balance(accounts.get(command.origen().toString())).compareTo(command.monto())<0) reason="SALDO_INSUFICIENTE";
  else if(command.destino()!=null && balance(accounts.get(command.destino().toString())).add(command.monto()).compareTo(MAX_BALANCE)>0) reason="LIMITE_SALDO";
  if(reason!=null) return finish(command,hash,"RECHAZADO",reason);
  if(command.origen()!=null) movement(command,accounts.get(command.origen().toString()),command.monto().negate());
  if(command.destino()!=null) movement(command,accounts.get(command.destino().toString()),command.monto());
  return finish(command,hash,"APLICADO","OK");
 }
 private BigDecimal balance(Map<String,Object> account) { return (BigDecimal)account.get("saldo"); }
 private void movement(PaymentCommand command,Map<String,Object> account,BigDecimal amount) {
  String accountId=(String)account.get("account_id"); BigDecimal updated=balance(account).add(amount);
  db.update("UPDATE modern_accounts SET saldo=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE account_id=?",updated,accountId);
  db.update("INSERT INTO account_payment_ledger(payment_id,account_id,monto,saldo_posterior) VALUES(?,?,?,?)",command.pagoId().toString(),accountId,amount,updated);
  db.update("INSERT INTO modern_account_audit(audit_id,account_id,actor,accion,version) VALUES(?,?,?,?,?)",
   UUID.randomUUID().toString(),accountId,command.actor(),command.tipo(),((Number)account.get("version")).longValue()+1);
 }
 private PaymentResult finish(PaymentCommand command,String hash,String status,String reason) {
  var result=new PaymentResult(1,command.pagoId(),hash,status,reason); String payload=json.writeValueAsString(result);
  db.update("UPDATE account_payment_operations SET result_payload=? WHERE payment_id=?",payload,command.pagoId().toString());
  db.update("INSERT INTO account_payment_outbox(event_id,payload) VALUES(?,?)",command.pagoId().toString(),payload);
  return result;
 }
}
