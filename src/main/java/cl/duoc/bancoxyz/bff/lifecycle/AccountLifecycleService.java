package cl.duoc.bancoxyz.bff.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static cl.duoc.bancoxyz.bff.lifecycle.AccountContracts.*;

@Service
public class AccountLifecycleService {
 private final JdbcTemplate db;
 private final CustomerDirectory customers;
 private final ObjectMapper json;
 public AccountLifecycleService(JdbcTemplate db,CustomerDirectory customers,ObjectMapper json) { this.db=db; this.customers=customers; this.json=json; }
 private static final RowMapper<View> MAPPER=(r,n)->new View(UUID.fromString(r.getString("account_id")),UUID.fromString(r.getString("customer_id")),
  r.getString("tipo"),r.getString("alias"),r.getString("moneda"),r.getBigDecimal("saldo"),r.getString("estado"),r.getLong("version"));
 public View find(UUID id) {
  return db.query("SELECT * FROM modern_accounts WHERE account_id=?",MAPPER,id.toString()).stream().findFirst()
   .orElseThrow(()->failure(HttpStatus.NOT_FOUND,"Cuenta moderna no encontrada"));
 }
 public List<View> list(UUID customer,int page,int size) {
  if(customer==null) return db.query("SELECT * FROM modern_accounts ORDER BY created_at,account_id LIMIT ? OFFSET ?",MAPPER,size,(long)page*size);
  return db.query("SELECT * FROM modern_accounts WHERE customer_id=? ORDER BY created_at,account_id LIMIT ? OFFSET ?",MAPPER,customer.toString(),size,(long)page*size);
 }
 @Transactional public View open(UUID key,String actor,String token,Open input) {
  validateActor(actor);
  var normalized=new Open(input.clienteId(),input.tipo(),input.alias().trim());
  UUID id=UUID.nameUUIDFromBytes(("account:"+actor+":"+key).getBytes(StandardCharsets.UTF_8));
  String hash=hash(normalized);
  var previous=db.queryForList("SELECT creation_hash FROM modern_accounts WHERE account_id=?",id.toString());
  if(!previous.isEmpty()) { checkHash(previous.getFirst(),hash); return find(id); }
  customers.require(input.clienteId(),token);
  try {
   db.update("INSERT INTO modern_accounts(account_id,customer_id,creation_hash,created_by,tipo,alias) VALUES(?,?,?,?,?,?)",
    id.toString(),input.clienteId().toString(),hash,actor,input.tipo().name(),normalized.alias());
  } catch(DuplicateKeyException ex) {
   var row=db.queryForMap("SELECT creation_hash FROM modern_accounts WHERE account_id=? FOR UPDATE",id.toString());
   checkHash(row,hash);
   // MySQL REPEATABLE READ: lectura actual para observar el INSERT concurrente.
   return db.queryForObject("SELECT * FROM modern_accounts WHERE account_id=? FOR UPDATE",MAPPER,id.toString());
  }
  audit(id,actor,"APERTURA",0);
  return find(id);
 }
 @Transactional public View maintain(UUID id,String actor,Maintain input) {
  validateActor(actor);
  var old=locked(id,input.version());
  if(old.estado().equals("CERRADA")) throw failure(HttpStatus.CONFLICT,"Una cuenta cerrada no se puede modificar ni reabrir");
  db.update("UPDATE modern_accounts SET alias=?,estado=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE account_id=?",
   input.alias().trim(),input.estado().name(),id.toString());
  audit(id,actor,"MANTENIMIENTO",old.version()+1);
  return find(id);
 }
 @Transactional public View close(UUID id,String actor,long version) {
  validateActor(actor);
  var old=locked(id,version);
  if(old.estado().equals("CERRADA")) throw failure(HttpStatus.CONFLICT,"Cuenta ya cerrada");
  if(old.saldo().signum()!=0) throw failure(HttpStatus.CONFLICT,"El cierre requiere saldo cero");
  db.update("UPDATE modern_accounts SET estado='CERRADA',version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE account_id=?",id.toString());
  audit(id,actor,"CIERRE",old.version()+1);
  return find(id);
 }
 private View locked(UUID id,long version) {
  var rows=db.query("SELECT * FROM modern_accounts WHERE account_id=? FOR UPDATE",MAPPER,id.toString());
  if(rows.isEmpty()) throw failure(HttpStatus.NOT_FOUND,"Cuenta moderna no encontrada");
  var row=rows.getFirst();
  if(row.version()!=version) throw failure(HttpStatus.CONFLICT,"Version desactualizada; consultar la cuenta antes de modificar");
  return row;
 }
 private void audit(UUID id,String actor,String action,long version) {
  db.update("INSERT INTO modern_account_audit(audit_id,account_id,actor,accion,version) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),id.toString(),actor,action,version);
 }
 private void checkHash(Map<String,Object> row,String hash) {
  if(!hash.equals(row.get("creation_hash"))) throw failure(HttpStatus.CONFLICT,"Idempotency-Key reutilizada con otro contenido");
 }
 private void validateActor(String actor) {
  if(actor==null||actor.isBlank()||actor.length()>100) throw failure(HttpStatus.FORBIDDEN,"Identidad no admitida");
 }
 private String hash(Open input) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input))); }
  catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 private ResponseStatusException failure(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
