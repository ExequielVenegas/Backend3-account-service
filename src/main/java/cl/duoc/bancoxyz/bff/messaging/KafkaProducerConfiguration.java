package cl.duoc.bancoxyz.bff.messaging;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lab.kafka.enabled", havingValue="true")
public class KafkaProducerConfiguration {
    @Bean ProducerFactory<String,String> commandProducerFactory(
            @Value("${lab.kafka.client-properties:}") String file,
            @Value("${lab.kafka.bootstrap-servers:localhost:9092}") String bootstrap) throws IOException {
        return new DefaultKafkaProducerFactory<>(KafkaClientSettings.producer(file, bootstrap));
    }
    @Bean KafkaTemplate<String,String> commandKafkaTemplate(ProducerFactory<String,String> factory) {
        return new KafkaTemplate<>(factory);
    }
}
