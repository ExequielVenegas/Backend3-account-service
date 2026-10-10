package cl.duoc.bancoxyz.bff.lifecycle;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class AccountContracts {
 private AccountContracts() {}
 public enum Type { AHORRO, CORRIENTE }
 public enum EditableState { ACTIVA, BLOQUEADA }
 public record Open(@NotNull UUID clienteId, @NotNull Type tipo, @NotBlank @Size(max=80) String alias) {}
 public record Maintain(@NotNull @Min(0) Long version, @NotBlank @Size(max=80) String alias, @NotNull EditableState estado) {}
 public record Close(@NotNull @Min(0) Long version) {}
 public record View(UUID cuentaId,UUID clienteId,String tipo,String alias,String moneda,BigDecimal saldo,String estado,long version) {}
}
