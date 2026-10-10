package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.lifecycle.*;
import cl.duoc.bancoxyz.bff.lifecycle.LegacyAssociationService.Decision;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:legacy_association;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LegacyAssociationTests {
 @Autowired LegacyAssociationService service;
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @MockitoBean CustomerDirectory customers;
 final UUID customer=UUID.fromString("f0000000-0000-0000-0000-000000000001");
 Decision input() { return new Decision(137,customer,"a".repeat(64),3,"2026-10","ahorro",new BigDecimal("100.00"),"Migracion revisada","Seleccion manual verificada del laboratorio",true); }
 @BeforeEach void clear() { db.update("DELETE FROM legacy_account_associations");db.update("DELETE FROM modern_account_audit");db.update("DELETE FROM modern_accounts"); }
 @Test void associatesCustomerAndProvenanceWithOriginalBalanceOnce() {
  var first=service.associate(input(),"web-demo","test-token");
  assertEquals(customer,first.clienteId());assertEquals("BLOQUEADA",first.estado());assertEquals(new BigDecimal("100.00"),first.saldo());
  assertEquals(first,service.associate(input(),"web-demo","test-token"));
  assertEquals(first.cuentaId(),service.find(137).cuentaId());assertEquals(3,service.find(137).linea());
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM modern_account_audit",Integer.class));
  verify(customers,times(1)).require(customer,"test-token");
 }
 @Test void replayDoesNotResetBalanceAfterNewOperations() {
  var first=service.associate(input(),"web-demo","test-token");
  db.update("UPDATE modern_accounts SET saldo=80,estado='ACTIVA',version=1 WHERE account_id=?",first.cuentaId().toString());
  var replay=service.associate(input(),"web-demo","test-token");
  assertEquals(new BigDecimal("80.00"),replay.saldo());assertEquals(1,replay.version());assertEquals("ACTIVA",replay.estado());
  assertEquals(new BigDecimal("100.00"),service.find(137).saldoOriginal());
 }
 @Test void changingSourceOrCustomerCannotOverwriteAssociation() {
  service.associate(input(),"web-demo","test-token");
  var changed=new Decision(137,UUID.randomUUID(),"b".repeat(64),5,"2026-11","ahorro",new BigDecimal("300.00"),"Cambio","Otra decision diferente",true);
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.associate(changed,"web-demo","token")).getStatusCode().value());
  assertEquals(customer,service.find(137).clienteId());
 }
 @Test void absentCustomerLeavesNoAccountOrAssociation() {
  doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)).when(customers).require(any(),any());
  assertThrows(ResponseStatusException.class,()->service.associate(input(),"web-demo","token"));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM modern_accounts",Integer.class));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM legacy_account_associations",Integer.class));
 }
 @Test void concurrentReplaysCreateOneAccountAndAudit() throws Exception {
  Callable<AccountContracts.View> call=()->service.associate(input(),"web-demo","token");
  try(var executor=Executors.newFixedThreadPool(3)) {
   var results=executor.invokeAll(List.of(call,call,call));var id=results.getFirst().get().cuentaId();
   for(var result:results) assertEquals(id,result.get().cuentaId());
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM legacy_account_associations",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM modern_account_audit",Integer.class));
 }
 @Test void importRequiresExplicitScopeAndValidDecision() throws Exception {
  String route="/internal/managed-accounts/importaciones";
  mvc.perform(post(route).contentType("application/json").content(json.writeValueAsString(input()))).andExpect(status().isUnauthorized());
  for(String scope:List.of("web.accounts.write","web.accounts.read","mobile.accounts.write"))
   mvc.perform(post(route).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_"+scope))).contentType("application/json").content(json.writeValueAsString(input()))).andExpect(status().isForbidden());
  var auth=jwt().jwt(j->j.subject("web-demo")).authorities(new SimpleGrantedAuthority("SCOPE_web.accounts.import"));
  mvc.perform(post(route).with(auth).contentType("application/json").content(json.writeValueAsString(input()).replace("\"revisado\":true","\"revisado\":false"))).andExpect(status().isBadRequest());
  mvc.perform(post(route).with(auth).contentType("application/json").content(json.writeValueAsString(input()).replace("ahorro","prestamo"))).andExpect(status().isBadRequest());
  mvc.perform(post(route).with(auth).contentType("application/json").content(json.writeValueAsString(input()))).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("BLOQUEADA"));
  mvc.perform(get(route+"/137").with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.archivoHash").value("a".repeat(64)));
 }
}
