package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.common.model.*;
import cl.duoc.bancoxyz.bff.common.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountServiceApplicationTests {
    @Autowired MockMvc mvc;
    @MockitoBean CalculoInteresRepository accounts;
    @MockitoBean EstadoCuentaAnualRepository movements;
    @MockitoBean TransaccionDiariaRepository transactions;
    @Test void contextLoads() {}
    @Test void returnsInternalAccountContract() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(new CalculoInteres(1,"Ana","AHORRO",bd("1000"),bd("50"),bd("1050"))));
        when(movements.findByCuentaId(1)).thenReturn(List.of(new EstadoCuentaAnual(7,1, LocalDate.of(2026,1,1),"DEPOSITO",bd("100"),"Abono",true)));
        mvc.perform(get("/internal/accounts/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1)).andExpect(jsonPath("$.availableBalance").value(1050))
                .andExpect(jsonPath("$.movements[0].description").value("Abono"));
    }
    @Test void missingAccountReturns404() throws Exception {
        when(accounts.findById(99)).thenReturn(Optional.empty());
        mvc.perform(get("/internal/accounts/99")).andExpect(status().isNotFound());
    }
    @Test void returnsMobileSummaryWithoutMovements() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(new CalculoInteres(1,"Ana","AHORRO",bd("1000"),bd("50"),bd("1050"))));
        mvc.perform(get("/internal/accounts/1/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1)).andExpect(jsonPath("$.customerName").value("Ana"))
                .andExpect(jsonPath("$.availableBalance").value(1050)).andExpect(jsonPath("$.movements").doesNotExist());
    }
    @Test void returnsAtmBalanceOnly() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(new CalculoInteres(1,"Ana","AHORRO",bd("1000"),bd("50"),bd("1050"))));
        mvc.perform(get("/internal/accounts/1/balance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1)).andExpect(jsonPath("$.availableBalance").value(1050))
                .andExpect(jsonPath("$.customerName").doesNotExist());
    }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
