package cl.duoc.bancoxyz.bff.account.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record TransactionResponse(Integer id, LocalDate date, BigDecimal amount, String type, String status) {}
