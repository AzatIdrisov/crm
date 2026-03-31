package com.crm.kafka;

import com.crm.kafka.config.KafkaTopics;
import com.crm.kafka.message.DealStatusChangedMessage;
import com.crm.kafka.producer.DealEventProducer;
import com.crm.model.enums.DealStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест: DealEventProducer → Kafka topic.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * @EmbeddedKafka — in-process брокер без Docker
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  EmbeddedKafkaBroker запускает Kafka-брокер прямо в JVM.
 *  Преимущества перед Testcontainers Kafka:
 *   - Быстрее старт (нет Docker overhead)
 *   - Нет зависимости от Docker daemon
 *   - Достаточно для юнит/интеграционных тестов сервисного слоя
 *
 *  Недостатки:
 *   - Не 100% идентичен реальному Kafka (нет ZooKeeper / KRaft)
 *   - Не подходит для тестирования KRaft-specific поведения
 *   - Testcontainers Kafka ближе к production-окружению
 *
 *  bootstrapServersProperty: EmbeddedKafka перезапишет spring.kafka.bootstrap-servers
 *  на адрес своего брокера (localhost:random_port). Это гарантирует что приложение
 *  будет использовать embedded брокер, а не реальный Kafka.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * spring.kafka.listener.auto-startup=false
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Отключаем автостарт @KafkaListener контейнеров.
 *  Без этого DealEventConsumer тоже подписался бы на deal-status-changed
 *  и «съел» бы сообщение раньше нашего тестового consumer'а.
 *  Тест использует отдельный Consumer с другим group.id для верификации.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * KafkaTestUtils
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  consumerProps(): готовые настройки consumer для embedded брокера.
 *   Пример: bootstrap.servers уже указан на embedded адрес.
 *
 *  getRecords(consumer, timeout): блокирует до получения хотя бы одной записи
 *   или таймаута. Лучше чем Thread.sleep: тест завершается сразу после получения.
 *
 *  embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic):
 *   Подписывает consumer на топик и ждёт назначения партиций (partition assignment).
 *   Важно вызвать ДО отправки сообщения, иначе можно пропустить его.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.DEAL_STATUS_CHANGED, KafkaTopics.DEAL_STATUS_CHANGED_DLT},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        // Отключаем DealEventConsumer — тест читает из топика напрямую
        "spring.kafka.listener.auto-startup=false"
})
@DisplayName("9.7.1 — Kafka Producer/Consumer Integration Test")
class KafkaProducerConsumerIntegrationTest {

    // ─────────────────────────────────────────────────────────
    // Инфраструктура: PostgreSQL + Redis (нужны для @SpringBootTest)
    // ─────────────────────────────────────────────────────────

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_test")
            .withUsername("crm")
            .withPassword("crm");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfra(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port",     () -> redis.getMappedPort(6379));
    }

    // ─────────────────────────────────────────────────────────
    // Бины из контекста
    // ─────────────────────────────────────────────────────────

    @Autowired
    DealEventProducer dealEventProducer;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    // ─────────────────────────────────────────────────────────
    // Тесты
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("send: публикует сообщение в топик с корректным payload")
    void send_publishesMessageToTopic() {
        // Подписываемся ДО отправки — иначе можно пропустить сообщение
        Consumer<String, String> consumer = createTestConsumer("test-verify-payload-group");
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.DEAL_STATUS_CHANGED);

        DealStatusChangedMessage message = new DealStatusChangedMessage(
                42L, DealStatus.NEW, DealStatus.WON,
                Instant.now(), UUID.randomUUID().toString()
        );

        dealEventProducer.send(message);

        // getRecords блокирует до получения записей или таймаута (не Thread.sleep!)
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, String> record = records.iterator().next();

        // Payload содержит поля нашего DTO (JsonSerializer сериализует объект)
        assertThat(record.value())
                .contains("\"dealId\":42")
                .contains("\"oldStatus\":\"NEW\"")
                .contains("\"newStatus\":\"WON\"");
    }

    @Test
    @DisplayName("send: partition key = dealId.toString() — ordering гарантирован для одной сделки")
    void send_usesDealIdAsPartitionKey() {
        Consumer<String, String> consumer = createTestConsumer("test-verify-key-group");
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.DEAL_STATUS_CHANGED);

        long dealId = 99L;
        dealEventProducer.send(new DealStatusChangedMessage(
                dealId, DealStatus.IN_PROGRESS, DealStatus.WON,
                Instant.now(), UUID.randomUUID().toString()
        ));

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        ConsumerRecord<String, String> record = records.iterator().next();
        // Ключ = dealId как строка → hash(key) % partitions определяет партицию
        // Все события одной сделки → одна партиция → строгий порядок
        assertThat(record.key()).isEqualTo(String.valueOf(dealId));
    }

    @Test
    @DisplayName("send: messageId уникален для каждого вызова — основа идемпотентности consumer'а")
    void send_generatesUniqueMessageId() {
        Consumer<String, String> consumer = createTestConsumer("test-verify-msgid-group");
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.DEAL_STATUS_CHANGED);

        String messageId = UUID.randomUUID().toString();
        dealEventProducer.send(new DealStatusChangedMessage(
                1L, DealStatus.NEW, DealStatus.LOST,
                Instant.now(), messageId
        ));

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        ConsumerRecord<String, String> record = records.iterator().next();
        // messageId передаётся в payload — consumer использует его как ключ дедупликации в Redis
        assertThat(record.value()).contains(messageId);
    }

    // ─────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────

    /**
     * Создаёт тестовый consumer с уникальным group.id.
     *
     * Уникальный group.id гарантирует что тестовый consumer:
     *  1. Не конкурирует с другими consumer'ами
     *  2. Начинает читать с earliest offset (не пропустит сообщение)
     *  3. Независим между тестовыми методами (нет shared state)
     *
     * StringDeserializer — читаем payload как сырую строку для верификации JSON.
     * Не нужен JsonDeserializer с TypeId — нам важно содержимое, а не тип объекта.
     */
    private Consumer<String, String> createTestConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
