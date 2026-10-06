package cl.duoc.bancoxyz.bff.account.dto;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        Integer accountId,
        String customerName,
        String accountType,
        BigDecimal availableBalance) {}
