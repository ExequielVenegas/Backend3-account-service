package cl.duoc.bancoxyz.bff.account.dto;

import java.math.BigDecimal;

public record AccountBalanceResponse(Integer accountId, BigDecimal availableBalance) {}
