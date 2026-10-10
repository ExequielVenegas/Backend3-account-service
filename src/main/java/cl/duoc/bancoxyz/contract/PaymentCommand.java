package cl.duoc.bancoxyz.contract;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Contrato v1: conservar identico en Payment y Account. Sin datos personales ni tokens. */
public record PaymentCommand(int version,UUID pagoId,String actor,String tipo,UUID origen,UUID destino,BigDecimal monto,String referencia) {
 public PaymentCommand normalized() {
  validate();
  return new PaymentCommand(version,pagoId,actor,tipo,origen,destino,monto.setScale(2),referencia.trim());
 }
 public void validate() {
  if(version!=1 || pagoId==null || actor==null || actor.isBlank() || actor.length()>100 || tipo==null ||
    !Set.of("DEPOSITO","TRANSFERENCIA","PAGO","RETIRO").contains(tipo) || monto==null ||
    monto.signum()<=0 || monto.scale()>2 || monto.compareTo(new BigDecimal("999999999999999.99"))>0 ||
    referencia==null || referencia.isBlank() || referencia.length()>140)
   throw new IllegalArgumentException("Contrato de pago invalido");
  boolean invalid=switch(tipo) {
   case "DEPOSITO" -> origen!=null || destino==null;
   case "RETIRO" -> origen==null || destino!=null;
   default -> origen==null || destino==null || origen.equals(destino);
  };
  if(invalid)
   throw new IllegalArgumentException("Cuentas de origen/destino invalidas");
 }
 public String fingerprint(ObjectMapper mapper) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(normalized()).getBytes(StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
}
