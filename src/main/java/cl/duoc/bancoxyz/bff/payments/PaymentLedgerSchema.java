package cl.duoc.bancoxyz.bff.payments;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
@Configuration(proxyBeanMethods=false)
public class PaymentLedgerSchema {
 @Bean @DependsOn("modernAccountSchema") InitializingBean initializePaymentLedger(DataSource source) {
  return ()->new ResourceDatabasePopulator(new ClassPathResource("payment-ledger-schema.sql")).execute(source);
 }
}
