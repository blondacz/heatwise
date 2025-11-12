package dev.g4s.heatwise.view;

import dev.g4s.heatwise.view.domain.Decision;
import dev.g4s.heatwise.view.domain.State;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ViewApplicationTest {
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:latest"));
    static Path tempStateDir;


    @BeforeAll
    public static void setUpBeforeAllTests() throws ExecutionException, InterruptedException, IOException {
       kafka.start();

       // Create a unique temporary directory for Kafka Streams state
       tempStateDir = Files.createTempDirectory("kafka-streams-test-");

       // Create topics before Spring Boot starts
       Map<String, Object> config = new HashMap<>();
       config.put("bootstrap.servers", kafka.getBootstrapServers());

       try (AdminClient admin = AdminClient.create(config)) {
           admin.createTopics(List.of(
               new NewTopic("heatwise.state", 1, (short) 1),
               new NewTopic("heatwise.decisions", 1, (short) 1)
           )).all().get();
       }
    }


    @AfterAll
    public static void tearDownAfterAllTests() {
        kafka.stop();
    }



    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.streams.state-dir", () -> tempStateDir.toString());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    private void waitForKafkaStreamsToBeRunning() {
        await().atMost(30, SECONDS).untilAsserted(() -> {
            KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();
            assertThat(kafkaStreams).isNotNull();
            assertThat(kafkaStreams.state()).isEqualTo(KafkaStreams.State.RUNNING);
        });
    }

    @Test
    void applicationStartsAndProcessesMessages() throws Exception {
        waitForKafkaStreamsToBeRunning();

        String deviceId = "test-device-1";
        Instant now = Instant.now();

        // Create Kafka producer
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(producerProps)) {
            State state = new State(deviceId, now, true);
            producer.send(new ProducerRecord<>("heatwise.state", deviceId, state)).get();

            Decision decision = new Decision(deviceId, false, "Test reason", now);
            producer.send(new ProducerRecord<>("heatwise.decisions", deviceId, decision)).get();
        }

        await().atMost(30, SECONDS).untilAsserted(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/view/device/" + deviceId,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            String body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body).contains("\"deviceId\":\"" + deviceId + "\"");
            assertThat(body).contains("\"lastDecisionReason\":\"Test reason\"");
            assertThat(body).contains("\"lastDecisionOn\":false");
        });
    }

    @Test
    void canRetrieveAllDevices() throws Exception {
        waitForKafkaStreamsToBeRunning();

        String deviceId1 = "test-device-2";
        String deviceId2 = "test-device-3";
        Instant now = Instant.now();

        // Create Kafka producer
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(producerProps)) {
            // Send messages for two devices
            producer.send(new ProducerRecord<>("heatwise.state", deviceId1, new State(deviceId1, now, true))).get();
            producer.send(new ProducerRecord<>("heatwise.decisions", deviceId1, new Decision(deviceId1, true, "Reason 1", now))).get();

            producer.send(new ProducerRecord<>("heatwise.state", deviceId2, new State(deviceId2, now, false))).get();
            producer.send(new ProducerRecord<>("heatwise.decisions", deviceId2, new Decision(deviceId2, false, "Reason 2", now))).get();
        }

        // Wait for processing and verify all devices endpoint
        await().atMost(30, SECONDS).untilAsserted(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/view/devices", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            String body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body).contains(deviceId1);
            assertThat(body).contains(deviceId2);
        });
    }
}
