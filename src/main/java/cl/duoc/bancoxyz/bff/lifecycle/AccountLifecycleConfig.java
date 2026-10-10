package cl.duoc.bancoxyz.bff.lifecycle;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods=false)
public class AccountLifecycleConfig {
 @Bean InitializingBean modernAccountSchema(DataSource dataSource) {
  return () -> new ResourceDatabasePopulator(new ClassPathResource("modern-accounts-schema.sql"),new ClassPathResource("legacy-associations-schema.sql")).execute(dataSource);
 }
 @Bean SecurityFilterChain modernAccountSecurity(HttpSecurity http) throws Exception {
  // Las consultas legacy mantienen su contrato interno existente. Las nuevas rutas validan JWT.
  return http.securityMatcher("/internal/managed-accounts", "/internal/managed-accounts/**")
   .csrf(AbstractHttpConfigurer::disable)
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a
    .requestMatchers("/internal/managed-accounts/importaciones","/internal/managed-accounts/importaciones/**").hasAuthority("SCOPE_web.accounts.import")
    .requestMatchers(HttpMethod.GET,"/internal/managed-accounts/**","/internal/managed-accounts").hasAnyAuthority("SCOPE_web.accounts.read","SCOPE_mobile.accounts.read","SCOPE_atm.accounts.read")
    .requestMatchers(HttpMethod.POST,"/internal/managed-accounts").hasAnyAuthority("SCOPE_web.accounts.write","SCOPE_mobile.accounts.write")
    .requestMatchers(HttpMethod.POST,"/internal/managed-accounts/*/cierre").hasAuthority("SCOPE_web.accounts.write")
    .requestMatchers(HttpMethod.PUT,"/internal/managed-accounts/*").hasAuthority("SCOPE_web.accounts.write")
    .anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults())).build();
 }
}
