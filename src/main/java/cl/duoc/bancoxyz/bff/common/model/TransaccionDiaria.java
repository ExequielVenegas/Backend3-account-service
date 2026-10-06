package cl.duoc.bancoxyz.bff.common.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Table("transacciones_diarias")
public record TransaccionDiaria(
        @Id Integer id,
        LocalDate fecha,
        BigDecimal monto,
        String tipo,
        String estado
) {
}
