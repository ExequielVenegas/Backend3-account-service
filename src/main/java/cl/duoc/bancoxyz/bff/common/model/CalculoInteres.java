package cl.duoc.bancoxyz.bff.common.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("calculo_intereses")
public record CalculoInteres(
        @Id @Column("cuenta_id") Integer cuentaId,
        String nombre,
        String tipo,
        @Column("saldo_original") BigDecimal saldoOriginal,
        @Column("interes_aplicado") BigDecimal interesAplicado,
        @Column("saldo_final") BigDecimal saldoFinal
) {
}
