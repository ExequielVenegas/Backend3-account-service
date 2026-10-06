package cl.duoc.bancoxyz.bff.account.service;
import cl.duoc.bancoxyz.bff.account.dto.*;
import cl.duoc.bancoxyz.bff.common.exception.ResourceNotFoundException;
import cl.duoc.bancoxyz.bff.common.model.EstadoCuentaAnual;
import cl.duoc.bancoxyz.bff.common.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService {
    private final CalculoInteresRepository accounts;
    private final EstadoCuentaAnualRepository movements;
    private final TransaccionDiariaRepository transactions;
    public AccountQueryService(CalculoInteresRepository accounts, EstadoCuentaAnualRepository movements,
            TransaccionDiariaRepository transactions) {
        this.accounts = accounts; this.movements = movements; this.transactions = transactions;
    }
    public AccountDetailsResponse findAccount(Integer id) {
        var account = accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("No existe la cuenta " + id));
        var items = movements.findByCuentaId(id).stream().map(this::movement).toList();
        return new AccountDetailsResponse(account.cuentaId(), account.nombre(), account.tipo(),
                account.saldoOriginal(), account.interesAplicado(), account.saldoFinal(), items);
    }
    public AccountSummaryResponse findSummary(Integer id) {
        var account = accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("No existe la cuenta " + id));
        return new AccountSummaryResponse(account.cuentaId(), account.nombre(), account.tipo(), account.saldoFinal());
    }
    public AccountBalanceResponse findBalance(Integer id) {
        var account = accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("No existe la cuenta " + id));
        return new AccountBalanceResponse(account.cuentaId(), account.saldoFinal());
    }
    public TransactionResponse findTransaction(Integer id) {
        var item = transactions.findById(id).orElseThrow(() -> new ResourceNotFoundException("No existe la transacción " + id));
        return new TransactionResponse(item.id(), item.fecha(), item.monto(), item.tipo(), item.estado());
    }
    private AccountMovementResponse movement(EstadoCuentaAnual item) {
        return new AccountMovementResponse(item.id(), item.fecha(), item.tipoTransaccion(), item.monto(),
                item.descripcion(), item.auditado());
    }
}
