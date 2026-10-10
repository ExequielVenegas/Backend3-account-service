package cl.duoc.bancoxyz.bff.lifecycle;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CustomerDirectory {
 private final RestClient client;
 public CustomerDirectory(@Qualifier("customerDirectoryBuilder") RestClient.Builder builder,
   @Value("${services.customer.base-url}") String url) { client=builder.baseUrl(url).build(); }
 public void require(UUID id,String token) {
  try { client.get().uri("/internal/customers/{id}/existencia",id).headers(h->h.setBearerAuth(token)).retrieve().toBodilessEntity(); }
  catch(HttpClientErrorException.NotFound ex) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Cliente inexistente; crear el perfil antes de abrir la cuenta"); }
  catch(HttpClientErrorException ex) { throw new ResponseStatusException(HttpStatus.valueOf(ex.getStatusCode().value()),"No se pudo autorizar la validacion del cliente"); }
  catch(RestClientException|IllegalStateException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Clientes no disponible; no se abrio la cuenta"); }
 }
 @Configuration(proxyBeanMethods=false)
 static class Clients {
  // Eureka debe conectarse directamente antes de poder descubrir Clientes.
  @Bean @Primary @Scope("prototype") RestClient.Builder restClientBuilder() { return RestClient.builder(); }
  private RestClient.Builder builder() {
   var http=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
   var factory=new JdkClientHttpRequestFactory(http); factory.setReadTimeout(Duration.ofSeconds(2));
   return RestClient.builder().requestFactory(factory);
  }
  @Bean("customerDirectoryBuilder") @Profile("!cloud") RestClient.Builder local() { return builder(); }
  @Bean("customerDirectoryBuilder") @Profile("cloud") @LoadBalanced RestClient.Builder cloud() { return builder(); }
 }
}
