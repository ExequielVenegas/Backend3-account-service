package cl.duoc.bancoxyz.contract;
import java.util.*;
public record PaymentResult(int version,UUID pagoId,String commandHash,String estado,String motivo) {
 public void validate() {
  if(version!=1 || pagoId==null || commandHash==null || !commandHash.matches("[0-9a-f]{64}") ||
    estado==null || !Set.of("APLICADO","RECHAZADO").contains(estado) || motivo==null || motivo.length()>100 || motivo.isBlank())
   throw new IllegalArgumentException("Resultado de pago invalido");
 }
}
