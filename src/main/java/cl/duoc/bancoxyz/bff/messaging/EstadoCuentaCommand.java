package cl.duoc.bancoxyz.bff.messaging;
import jakarta.validation.constraints.*;
import java.util.UUID;
// Contrato JSON v1 compartido por valor; no depende de nombres de clases Java.
public record EstadoCuentaCommand(
        @NotNull UUID solicitudId,
        @Min(1) @Max(1) int version,
        @NotBlank @Pattern(regexp="SOLICITAR_ESTADO_CUENTA") String tipo,
        @NotBlank @Size(max=100) String cliente,
        @NotNull @Min(1) Integer cuentaId,
        @NotNull @Min(2000) @Max(2100) Integer anio) {}
