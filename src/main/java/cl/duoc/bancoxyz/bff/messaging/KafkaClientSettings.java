package cl.duoc.bancoxyz.bff.messaging;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
final class KafkaClientSettings {
    static Map<String,Object> load(String file, String bootstrap) throws IOException {
        var result = new HashMap<String,Object>();
        if (!file.isBlank()) {
            var properties = new Properties();
            try (var input = Files.newInputStream(Path.of(file))) { properties.load(input); }
            properties.forEach((key, value) -> result.put(key.toString(), value));
        }
        result.put("bootstrap.servers", bootstrap);
        // Evitar que la configuracion JAAS aparezca en los logs; Kafka la trata como Password.
        return result;
    }
    static Map<String,Object> producer(String file, String bootstrap) throws IOException {
        var result = load(file, bootstrap);
        result.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        result.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        result.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        result.put(ProducerConfig.ACKS_CONFIG, "all");
        result.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        result.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        result.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000);
        return result;
    }
}
