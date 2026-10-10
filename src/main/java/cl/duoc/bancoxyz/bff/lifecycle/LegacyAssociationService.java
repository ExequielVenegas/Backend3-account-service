package cl.duoc.bancoxyz.bff.lifecycle;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class LegacyAssociationService {
 public record Decision(@Min(1) int legacyId, @NotNull UUID clienteId,
  @NotBlank @Pattern(regexp="[a-f0-9]{64}") String archivoHash, @Min(2) int linea,
  @NotBlank @Pattern(regexp="[0-9]{4}-(0[1-9]|1[0-2])") String periodo,
  @NotBlank @Pattern(regexp="ahorro") String tipoOrigen,
  @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal saldoOriginal,
  @NotBlank @Size(max=80) String alias, @NotBlank @Size(min=10,max=500) String motivo,
  @NotNull @AssertTrue Boolean revisado) {}
 public record Association(int legacyId, UUID cuentaId, UUID clienteId, String archivoHash,
  int linea, String periodo, BigDecimal saldoOriginal, String motivo, String actor) {}
 private final JdbcTemplate db;
 private final CustomerDirectory customers;
 private final AccountLifecycleService accounts;
 private final ObjectMapper json;
 public LegacyAssociationService(JdbcTemplate db,CustomerDirectory customers,AccountLifecycleService accounts,ObjectMapper json) {
  this.db=db;this.customers=customers;this.accounts=accounts;this.json=json;
 }
 @Transactional public AccountContracts.View associate(Decision input,String actor,String token) {
  if(actor==null||actor.isBlank()||actor.length()>100) throw failure(HttpStatus.FORBIDDEN,"Identidad no admitida");
  if(!Boolean.TRUE.equals(input.revisado())||!"ahorro".equals(input.tipoOrigen()))
   throw failure(HttpStatus.UNPROCESSABLE_ENTITY,"Se requiere ahorro revisado; no se convierten prestamos o hipotecas");
  var normalized=new Decision(input.legacyId(),input.clienteId(),input.archivoHash(),input.linea(),input.periodo(),
   input.tipoOrigen(),input.saldoOriginal().setScale(2),input.alias().trim(),input.motivo().trim(),true);
  String hash=hash(normalized);
  UUID id=UUID.nameUUIDFromBytes(("eft-legacy-account:"+input.legacyId()).getBytes(StandardCharsets.UTF_8));
  var existing=db.queryForList("SELECT decision_hash FROM legacy_account_associations WHERE legacy_id=?",input.legacyId());
  if(!existing.isEmpty()) { check(existing.getFirst(),hash);return accounts.find(id); }
  customers.require(input.clienteId(),token);
  try {
   db.update("INSERT INTO modern_accounts(account_id,customer_id,creation_hash,created_by,tipo,alias,saldo,estado) VALUES(?,?,?,?,?,?,?,'BLOQUEADA')",
    id.toString(),input.clienteId().toString(),hash,actor,"AHORRO",normalized.alias(),normalized.saldoOriginal());
  } catch(DuplicateKeyException e) {
   var row=db.queryForMap("SELECT decision_hash FROM legacy_account_associations WHERE legacy_id=? FOR UPDATE",input.legacyId());
   check(row,hash);
   // A locking read sees the winning transaction under MySQL REPEATABLE READ.
   return db.queryForObject("SELECT * FROM modern_accounts WHERE account_id=? FOR UPDATE",(r,n)->
    new AccountContracts.View(id,UUID.fromString(r.getString("customer_id")),r.getString("tipo"),r.getString("alias"),
     r.getString("moneda"),r.getBigDecimal("saldo"),r.getString("estado"),r.getLong("version")),id.toString());
  }
  db.update("INSERT INTO legacy_account_associations(legacy_id,account_id,customer_id,source_hash,source_line,source_period,source_balance,decision_hash,decision_reason,actor) VALUES(?,?,?,?,?,?,?,?,?,?)",
   input.legacyId(),id.toString(),input.clienteId().toString(),input.archivoHash(),input.linea(),input.periodo(),normalized.saldoOriginal(),hash,normalized.motivo(),actor);
  db.update("INSERT INTO modern_account_audit(audit_id,account_id,actor,accion,version) VALUES(?,?,?,'IMPORTACION_LEGACY',0)",UUID.randomUUID().toString(),id.toString(),actor);
  return accounts.find(id);
 }
 public Association find(int id) {
  return db.query("SELECT * FROM legacy_account_associations WHERE legacy_id=?",(r,n)->new Association(
   r.getInt("legacy_id"),UUID.fromString(r.getString("account_id")),UUID.fromString(r.getString("customer_id")),
   r.getString("source_hash"),r.getInt("source_line"),r.getString("source_period"),r.getBigDecimal("source_balance"),
   r.getString("decision_reason"),r.getString("actor")),id).stream().findFirst()
   .orElseThrow(()->failure(HttpStatus.NOT_FOUND,"Cuenta legacy EFT sin asociacion"));
 }
 private String hash(Decision input) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input))); }
  catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 private void check(Map<String,Object> row,String hash) {
  if(!hash.equals(row.get("decision_hash"))) throw failure(HttpStatus.CONFLICT,"Cuenta legacy ya asociada con otra decision; no se modifica saldo ni titular");
 }
 private ResponseStatusException failure(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
