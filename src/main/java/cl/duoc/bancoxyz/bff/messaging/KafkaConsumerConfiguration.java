package cl.duoc.bancoxyz.bff.messaging;
import java.io.IOException;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lab.kafka.enabled", havingValue="true")
public class KafkaConsumerConfiguration {
    @Bean InitializingBean solicitudSchema(DataSource dataSource) {
        return () -> new ResourceDatabasePopulator(new ClassPathResource("solicitudes-schema.sql")).execute(dataSource);
    }
    @Bean ConcurrentKafkaListenerContainerFactory<String,String> commandListenerFactory(
            @Value("${lab.kafka.client-properties:}") String file,
            @Value("${lab.kafka.bootstrap-servers:localhost:9092}") String bootstrap,
            @Value("${lab.kafka.dlt:bancoxyz.cuentas.commands.v1.dlt}") String dlt,
            @org.springframework.beans.factory.annotation.Qualifier("commandKafkaTemplate") KafkaTemplate<String,String> template) throws IOException {
        var properties=KafkaClientSettings.load(file,bootstrap);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG,false);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,10);
        var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        var recoverer=new DeadLetterPublishingRecoverer(template, (record,error) -> new TopicPartition(dlt,record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler=new DefaultErrorHandler(recoverer,new FixedBackOff(1000,2));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}
