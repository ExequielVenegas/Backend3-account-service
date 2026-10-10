package cl.duoc.bancoxyz.bff.payments;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
@Configuration(proxyBeanMethods=false) @EnableScheduling
@ConditionalOnProperty(name="payments.kafka.enabled",havingValue="true")
public class AccountPaymentKafkaConfig {
 private Map<String,Object> settings(String file,String bootstrap) throws IOException {
  var result=new HashMap<String,Object>();
  if(!file.isBlank()) {
   var p=new Properties(); try(var in=Files.newInputStream(Path.of(file))) { p.load(in); }
   p.forEach((k,v)->result.put(k.toString(),v));
  }
  result.put("bootstrap.servers",bootstrap); return result;
 }
 @Bean("paymentKafkaTemplate") KafkaTemplate<String,String> template(
  @Value("${payments.kafka.client-properties:}") String file,@Value("${payments.kafka.bootstrap-servers:localhost:9092}") String bootstrap) throws IOException {
  var p=settings(file,bootstrap);
  p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class); p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
  p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true); p.put(ProducerConfig.ACKS_CONFIG,"all");
  p.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,3000); p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,3000); p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,5000);
  return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(p));
 }
 @Bean("paymentListenerFactory") ConcurrentKafkaListenerContainerFactory<String,String> listener(
  @Value("${payments.kafka.client-properties:}") String file,@Value("${payments.kafka.bootstrap-servers:localhost:9092}") String bootstrap,
  @Value("${payments.kafka.dlt:bancoxyz.pagos.commands.v1.dlt}") String dlt,@Qualifier("paymentKafkaTemplate") KafkaTemplate<String,String> template) throws IOException {
  var p=settings(file,bootstrap);
  p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class); p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
  p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false); p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
  p.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG,false); p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,10);
  var factory=new ConcurrentKafkaListenerContainerFactory<String,String>(); factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(p));
  factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
  var recoverer=new DeadLetterPublishingRecoverer(template,(record,error)->new TopicPartition(dlt,record.partition()));
  recoverer.setFailIfSendResultIsError(true);
  var handler=new DefaultErrorHandler(recoverer,new FixedBackOff(1000,2)); handler.addNotRetryableExceptions(IllegalArgumentException.class);
  factory.setCommonErrorHandler(handler); return factory;
 }
}
