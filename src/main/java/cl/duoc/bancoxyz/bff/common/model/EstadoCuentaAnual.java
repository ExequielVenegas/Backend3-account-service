package cl.duoc.bancoxyz.bff.common.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Table("estado_cuenta_anual")
public record EstadoCuentaAnual(
        @Id Integer id,
        @Column("cuenta_id") Integer cuentaId,
        LocalDate fecha,
        @Column("tipo_transaccion") String tipoTransaccion,
        BigDecimal monto,
        String descripcion,
        Boolean auditado
) {
}
