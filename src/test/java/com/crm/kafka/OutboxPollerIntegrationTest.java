package com.crm.kafka;

import com.crm.kafka.config.KafkaTopics;
import com.crm.kafka.message.DealStatusChangedMessage;
import com.crm.model.enums.DealStatus;
import com.crm.outbox.OutboxMessage;
import com.crm.outbox.OutboxPoller;
import com.crm.outbox.OutboxRepository;
import com.crm.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.awaitility.Awaitility.await;

/**
 * Интеграционный тест Outbox Poller end-to-end.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * СЦЕНАРИЙ ТЕСТА
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  1. Сохраняем OutboxMessage(status=PENDING) напрямую в БД.
 *     (Имитируем то, что делает DealService.changeStatus())
 *
 *  2. Вызываем outboxPoller.poll() вручную.
 *     (Не ждём @Scheduled — тест должен быть детерминированным)
 *
 *  3. Awaitility ждёт пока статус не станет SENT.
 *     Почему async? KafkaTemplate.send() возвращает CompletableFuture.
 *     markAsSent() вызывается в whenComplete-callback (другой поток).
 *     Awaitility опрашивает БД до 5 секунд вместо Thread.sleep(5000).
 *
 *  4. Проверяем что сообщение появилось в Kafka-топике.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * AWAITILITY vs Thread.sleep
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Thread.sleep(5000): тест всегда ждёт 5 секунд, даже если операция завершилась за 100ms.
 *
 *  await().atMost(5, SECONDS).until(...):
 *   - Опрашивает условие каждые pollInterval миллисекунд
 *   - Завершается СРАЗУ как только условие выполнено
 *   - Тест быстрее в happy path, но всё равно защищён таймаутом
 *   - При сбое: выбрасывает ConditionTimeoutException с описанием последнего состояния
 *
 *  Когда обязательно использовать Awaitility:
 *   - Асинхронные операции: @Async, @Scheduled, CompletableFuture callbacks
 *   - Event-driven: проверить что событие было обработано через N мс
 *   - Интеграционные тесты с внешними системами (Kafka, Redis)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ПОЧЕМУ ВЫЗЫВАЕМ poll() ВРУЧНУЮ, А НЕ ЖДЁМ @Scheduled
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  @Scheduled(initialDelay=5000) — 5 секунд ожидания до первого запуска.
 *  В тестах это слишком медленно и недетерминировано.
 *
 *  Прямой вызов poll() — тест контролирует момент запуска.
 *  @Scheduled и ручной вызов не конкурируют: @Scheduled запустится через 5с
 *  после старта контекста, к тому моменту тест уже завершён.
 *
 *  Если нужно протестировать именно @Scheduled-поведение — используй:
 *   await().atMost(15, SECONDS).until(...)  // ждём после реального triggering
 *  Но для unit/integration тестов прямой вызов предпочтительнее.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.DEAL_STATUS_CHANGED, KafkaTopics.DEAL_STATUS_CHANGED_DLT},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@DisplayName("9.7.2 — Outbox Poller Integration Test")
class OutboxPollerIntegrationTest {

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

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    OutboxPoller outboxPoller;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("poll: PENDING запись публикуется в Kafka и статус меняется на SENT")
    void poll_publishesPendingMessageAndMarksSent() throws Exception {
        // 1. Подписываемся на топик ДО poll() — не пропустим сообщение
        Consumer<String, String> consumer = createTestConsumer("test-outbox-verify-group");
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.DEAL_STATUS_CHANGED);

        // 2. Сохраняем PENDING-запись (имитация DealService.changeStatus())
        String messageId = UUID.randomUUID().toString();
        DealStatusChangedMessage dto = new DealStatusChangedMessage(
                100L, DealStatus.NEW, DealStatus.WON, Instant.now(), messageId
        );
        OutboxMessage outbox = OutboxMessage.builder()
                .aggregateType("Deal")
                .aggregateId("100")
                .eventType("DealStatusChanged")
                .payload(objectMapper.writeValueAsString(dto))
                .messageId(messageId)
                .status(OutboxStatus.PENDING)
                .build();
        OutboxMessage saved = outboxRepository.save(outbox);

        // 3. Запускаем poller вручную (не ждём @Scheduled)
        outboxPoller.poll();

        // 4. Awaitility: статус обновляется в whenComplete-callback (другой поток)
        //    poll() не ждёт завершения async callback — нужен Awaitility
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    OutboxMessage updated = outboxRepository.findById(saved.getId()).orElseThrow();
                    assertThat(updated.getStatus())
                            .as("Outbox status should change to SENT after successful Kafka send")
                            .isEqualTo(OutboxStatus.SENT);
                    assertThat(updated.getProcessedAt())
                            .as("processedAt should be set when SENT")
                            .isNotNull();
                });

        // 5. Проверяем что сообщение пришло в Kafka
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        consumer.close();

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, String> record = records.iterator().next();

        // aggregateId = partition key → ordering по сделке
        assertThat(record.key()).isEqualTo("100");

        // Payload содержит исходные данные DTO
        assertThat(record.value())
                .contains("\"dealId\":100")
                .contains("\"newStatus\":\"WON\"")
                .contains(messageId);
    }

    @Test
    @DisplayName("poll: PENDING → SENT, processedAt заполняется")
    void poll_setsProcessedAt() throws Exception {
        Instant beforePoll = Instant.now();

        String messageId = UUID.randomUUID().toString();
        DealStatusChangedMessage dto = new DealStatusChangedMessage(
                200L, DealStatus.IN_PROGRESS, DealStatus.WON, Instant.now(), messageId
        );
        OutboxMessage outbox = OutboxMessage.builder()
                .aggregateType("Deal")
                .aggregateId("200")
                .eventType("DealStatusChanged")
                .payload(objectMapper.writeValueAsString(dto))
                .messageId(messageId)
                .status(OutboxStatus.PENDING)
                .build();
        OutboxMessage saved = outboxRepository.save(outbox);

        outboxPoller.poll();

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    OutboxMessage updated = outboxRepository.findById(saved.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OutboxStatus.SENT);
                    // processedAt устанавливается в момент markAsSent() → после beforePoll
                    assertThat(updated.getProcessedAt()).isAfter(beforePoll);
                });
    }

    @Test
    @DisplayName("poll: пустой outbox — ничего не отправляется, ошибок нет")
    void poll_emptyOutbox_doesNothing() {
        // Нет PENDING-записей → poll() должен завершиться без ошибок
        outboxPoller.poll(); // должен вернуться без исключений
    }

    private Consumer<String, String> createTestConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
