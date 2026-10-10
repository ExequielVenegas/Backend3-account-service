package cl.duoc.bancoxyz.bff;

import cl.duoc.bancoxyz.bff.lifecycle.*;
import static cl.duoc.bancoxyz.bff.lifecycle.AccountContracts.*;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountLifecycleTests {
 @Autowired AccountLifecycleService service;
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 @MockitoBean CustomerDirectory customers;
 final UUID customer=UUID.fromString("f0000000-0000-0000-0000-000000000001");
 Open input() { return new Open(customer,Type.AHORRO,"Mi ahorro"); }
 View open() { return service.open(UUID.randomUUID(),"web-demo","test-token",input()); }
 @BeforeEach void resetData() { db.update("DELETE FROM modern_account_audit"); db.update("DELETE FROM modern_accounts"); }
 @Test void opensZeroBalanceAndAuditsOnceForDuplicate() {
  UUID key=UUID.randomUUID(); var first=service.open(key,"web-demo","test-token",input());
  assertEquals(first,service.open(key,"web-demo","test-token",input()));
  assertEquals(0,first.saldo().signum()); assertEquals("ACTIVA",first.estado()); assertEquals(customer,first.clienteId());
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM modern_account_audit",Integer.class));
  verify(customers,times(1)).require(customer,"test-token");
 }
 @Test void conflictingKeyCannotChangeOwnerOrType() {
  UUID key=UUID.randomUUID(); service.open(key,"web-demo","test-token",input());
  var ex=assertThrows(ResponseStatusException.class,()->service.open(key,"web-demo","test-token",new Open(UUID.randomUUID(),Type.CORRIENTE,"Otra")));
  assertEquals(409,ex.getStatusCode().value());
 }
 @Test void unknownCustomerOrUnavailableDirectoryLeavesNoAccount() {
  doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)).when(customers).require(any(),any());
  assertThrows(ResponseStatusException.class,this::open);
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM modern_accounts",Integer.class));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM modern_account_audit",Integer.class));
 }
 @Test void concurrentOpeningCreatesOneAccountAndAudit() throws Exception {
  UUID key=UUID.randomUUID();
  try(var pool=Executors.newFixedThreadPool(3)) {
   List<Callable<View>> calls=List.of(()->service.open(key,"web-demo","test-token",input()),()->service.open(key,"web-demo","test-token",input()),()->service.open(key,"web-demo","test-token",input()));
   var results=pool.invokeAll(calls); var id=results.getFirst().get().cuentaId();
   for(var result:results) assertEquals(id,result.get().cuentaId());
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM modern_accounts",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM modern_account_audit",Integer.class));
 }
 @Test void maintenancePreservesOwnerTypeAndBalanceAndRejectsStaleVersion() {
  var first=open(); var updated=service.maintain(first.cuentaId(),"web-demo",new Maintain(0L,"Reservas",EditableState.BLOQUEADA));
  assertEquals("BLOQUEADA",updated.estado()); assertEquals(1,updated.version());
  assertEquals(first.clienteId(),updated.clienteId()); assertEquals(first.tipo(),updated.tipo()); assertEquals(first.saldo(),updated.saldo());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.maintain(first.cuentaId(),"web-demo",new Maintain(0L,"Vieja",EditableState.ACTIVA))).getStatusCode().value());
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM modern_account_audit",Integer.class));
 }
 @Test void closureRequiresZeroAndClosedAccountCannotReopen() {
  var first=open();
  db.update("UPDATE modern_accounts SET saldo=100 WHERE account_id=?",first.cuentaId().toString());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.close(first.cuentaId(),"web-demo",0)).getStatusCode().value());
  assertEquals("ACTIVA",service.find(first.cuentaId()).estado());
  db.update("UPDATE modern_accounts SET saldo=0 WHERE account_id=?",first.cuentaId().toString());
  assertEquals("CERRADA",service.close(first.cuentaId(),"web-demo",0).estado());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.maintain(first.cuentaId(),"web-demo",new Maintain(1L,"Reabrir",EditableState.ACTIVA))).getStatusCode().value());
 }
 @Test void concurrentEditsWithSameVersionHaveOneWinner() throws Exception {
  var first=open();
  Callable<Boolean> edit=()->{ try { service.maintain(first.cuentaId(),"web-demo",new Maintain(0L,"Cambio",EditableState.BLOQUEADA)); return true; }
   catch(ResponseStatusException e) { assertEquals(409,e.getStatusCode().value()); return false; } };
  try(var pool=Executors.newFixedThreadPool(2)) {
   var results=pool.invokeAll(List.of(edit,edit)); int wins=0;
   for(var result:results) if(result.get()) wins++;
   assertEquals(1,wins);
  }
 }
 @Test void lifecycleRequiresSpecificPermissions() throws Exception {
  mvc.perform(get("/internal/managed-accounts")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/managed-accounts").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.read")))).andExpect(status().isForbidden());
  mvc.perform(get("/internal/managed-accounts").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.accounts.read")))).andExpect(status().isOk());
 }
 @Test void invalidInputAndMissingKeyAre400() throws Exception {
  var auth=jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.accounts.write"));
  mvc.perform(post("/internal/managed-accounts").with(auth).contentType("application/json").content("{}"))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/internal/managed-accounts").with(auth).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"tipo\":\"INVALIDA\"}"))
   .andExpect(status().isBadRequest());
 }
 @Test void listFiltersByCustomerAndUnknownAccountIs404() {
  open(); assertEquals(1,service.list(customer,0,20).size());
  assertTrue(service.list(UUID.randomUUID(),0,20).isEmpty());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.find(UUID.randomUUID())).getStatusCode().value());
 }
}
