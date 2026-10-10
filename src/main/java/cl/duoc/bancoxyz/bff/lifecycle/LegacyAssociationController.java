package cl.duoc.bancoxyz.bff.lifecycle;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import java.net.URI;
@RestController @RequestMapping("/internal/managed-accounts/importaciones")
public class LegacyAssociationController {
 private final LegacyAssociationService service;
 public LegacyAssociationController(LegacyAssociationService service) { this.service=service; }
 @PostMapping public ResponseEntity<AccountContracts.View> associate(@Valid @RequestBody LegacyAssociationService.Decision input,@AuthenticationPrincipal Jwt jwt) {
  var account=service.associate(input,jwt.getSubject(),jwt.getTokenValue());
  return ResponseEntity.created(URI.create("/internal/managed-accounts/"+account.cuentaId())).body(account);
 }
 @GetMapping("/{id}") public LegacyAssociationService.Association find(@PathVariable int id) { return service.find(id); }
}
