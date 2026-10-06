package cl.duoc.bancoxyz.bff.account.dto;
import java.math.BigDecimal;
import java.util.List;
public record AccountDetailsResponse(Integer accountId, String customerName, String accountType,
        BigDecimal originalBalance, BigDecimal interestAmount, BigDecimal availableBalance,
        List<AccountMovementResponse> movements) {}
