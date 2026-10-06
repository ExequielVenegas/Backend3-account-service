package cl.duoc.bancoxyz.bff.account.controller;
import cl.duoc.bancoxyz.bff.account.dto.AccountDetailsResponse;
import cl.duoc.bancoxyz.bff.account.dto.AccountBalanceResponse;
import cl.duoc.bancoxyz.bff.account.dto.AccountSummaryResponse;
import cl.duoc.bancoxyz.bff.account.dto.TransactionResponse;
import cl.duoc.bancoxyz.bff.account.service.AccountQueryService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/internal")
public class AccountController {
    private final AccountQueryService service;
    public AccountController(AccountQueryService service) { this.service = service; }
    @GetMapping("/accounts/{accountId}")
    public AccountDetailsResponse account(@PathVariable @Min(1) Integer accountId) { return service.findAccount(accountId); }
    @GetMapping("/accounts/{accountId}/summary")
    public AccountSummaryResponse summary(@PathVariable @Min(1) Integer accountId) { return service.findSummary(accountId); }
    @GetMapping("/accounts/{accountId}/balance")
    public AccountBalanceResponse balance(@PathVariable @Min(1) Integer accountId) { return service.findBalance(accountId); }
    @GetMapping("/transactions/{transactionId}")
    public TransactionResponse transaction(@PathVariable @Min(1) Integer transactionId) { return service.findTransaction(transactionId); }
}
