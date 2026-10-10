package cl.duoc.bancoxyz.bff.lifecycle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cl.duoc.bancoxyz.bff.lifecycle.AccountContracts.*;

@RestController @Validated @RequestMapping("/internal/managed-accounts")
public class AccountLifecycleController {
 private final AccountLifecycleService service;
 public AccountLifecycleController(AccountLifecycleService service) { this.service=service; }
 @PostMapping public ResponseEntity<View> open(@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Open input,@AuthenticationPrincipal Jwt jwt) {
  var result=service.open(key,jwt.getSubject(),jwt.getTokenValue(),input);
  return ResponseEntity.created(URI.create("/internal/managed-accounts/"+result.cuentaId())).body(result);
 }
 @GetMapping("/{id}") public View find(@PathVariable UUID id) { return service.find(id); }
 @GetMapping public List<View> list(@RequestParam(required=false) UUID clienteId,
  @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.list(clienteId,page,size); }
 @PutMapping("/{id}") public View maintain(@PathVariable UUID id,@Valid @RequestBody Maintain input,@AuthenticationPrincipal Jwt jwt) { return service.maintain(id,jwt.getSubject(),input); }
 @PostMapping("/{id}/cierre") public View close(@PathVariable UUID id,@Valid @RequestBody Close input,@AuthenticationPrincipal Jwt jwt) { return service.close(id,jwt.getSubject(),input.version()); }
}
