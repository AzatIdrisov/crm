package com.crm.kafka;

import com.crm.kafka.config.KafkaTopics;
import com.crm.kafka.consumer.DealEventConsumer;
import com.crm.kafka.message.DealStatusChangedMessage;
import com.crm.model.enums.DealStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
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
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Интеграционный тест: retry → Dead Letter Topic (DLT).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * СЦЕНАРИЙ: Poison Pill → DLT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  1. Отправляем валидное сообщение в deal-status-changed
 *  2. @SpyBean DealEventConsumer намеренно бросает RuntimeException
 *  3. DefaultErrorHandler перехватывает и применяет FixedBackOff(100ms, 3 retries)
 *  4. После исчерпания попыток → DeadLetterPublishingRecoverer → deal-status-changed.DLT
 *  5. Читаем DLT-сообщение и проверяем заголовки с метаданными ошибки
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * @SpyBean — частичный мок
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  @MockBean  — заменяет бин полностью. Все методы возвращают default value (null/false/0).
 *  @SpyBean   — оборачивает реальный бин. По умолчанию вызывает реальный метод.
 *              doThrow() / doReturn() переопределяют поведение конкретных методов.
 *
 *  Выбор @SpyBean вместо @MockBean:
 *   - DealEventConsumer имеет real init логику (зависимости в конструкторе)
 *   - Мы переопределяем ТОЛЬКО consume() → остальные методы работают как обычно
 *   - Это удобно для тестирования конкретного сбоя без полного замены бина
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FixedBackOff в тестах vs ExponentialBackOff в продакшене
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Продакшн: ExponentialBackOff(1s, 2.0, maxElapsedTime=30s) → ~4 попытки за 30с
 *  Тесты:    FixedBackOff(100ms, 3)                          → 4 попытки за ~400ms
 *
 *  FixedBackOff(interval, maxAttempts):
 *   maxAttempts = количество RETRY (повторных попыток), не считая первую.
 *   Итого вызовов consume() = 1 (initial) + maxAttempts = 1 + 3 = 4.
 *
 *  Почему переопределяем через @TestConfiguration, а не через @TestPropertySource:
 *   BackOff значения захардкожены в KafkaConfig, не читаются из application.yml.
 *   @TestConfiguration подменяет БИН целиком — единственный чистый способ.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DLT ЗАГОЛОВКИ (DeadLetterPublishingRecoverer)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  После исчерпания retry DeadLetterPublishingRecoverer публикует в DLT и добавляет:
 *
 *  DLT_ORIGINAL_TOPIC      — топик из которого пришло сообщение
 *  DLT_ORIGINAL_PARTITION  — номер партиции (byte[] значение числа)
 *  DLT_ORIGINAL_OFFSET     — offset записи
 *  DLT_EXCEPTION_FQCN      — полное имя класса исключения
 *  DLT_EXCEPTION_MESSAGE   — сообщение исключения
 *
 *  Эти заголовки позволяют: найти оригинальное сообщение по (topic, partition, offset),
 *  понять причину сбоя и сделать replay после исправления кода.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DLT consumer принимает byte[] (а не DealStatusChangedMessage)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  DLT-сообщение может быть невалидным (именно поэтому оно попало в DLT).
 *  byte[] позволяет прочитать его даже если десериализация в DTO невозможна.
 *  Это та же причина по которой DlqConsumer.consume() принимает byte[].
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.DEAL_STATUS_CHANGED, KafkaTopics.DEAL_STATUS_CHANGED_DLT},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        // Разрешаем переопределение бина kafkaListenerContainerFactory
        // из @TestConfiguration (заменяем ExponentialBackOff на FixedBackOff)
        "spring.main.allow-bean-definition-overriding=true"
})
@DisplayName("9.7.3 — DLT Integration Test: retry exhaustion → Dead Letter Topic")
class DlqIntegrationTest {

    // ─────────────────────────────────────────────────────────
    // Переопределяем фабрику только в этом тесте:
    // ExponentialBackOff(30s) → FixedBackOff(100ms × 3) для скорости
    // ─────────────────────────────────────────────────────────

    @TestConfiguration
    static class FastBackOffConfig {

        /**
         * Переопределяет kafkaListenerContainerFactory из KafkaConfig.
         *
         * @Primary не нужен — бин с тем же именем заменяет предыдущий
         * (при spring.main.allow-bean-definition-overriding=true).
         *
         * concurrency=1: @EmbeddedKafka создаёт 1 партицию,
         * concurrency=3 (из prod конфига) создал бы 2 idle потока.
         */
        @Bean
        public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
                ConsumerFactory<String, Object> consumerFactory,
                KafkaTemplate<String, Object> kafkaTemplate) {

            ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
            factory.setConcurrency(1);

            DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

            // FixedBackOff(interval=100ms, maxAttempts=3):
            //  1 initial attempt + 3 retries = 4 total calls → DLT
            //  Время: ~400ms vs ~30s в продакшне
            FixedBackOff backOff = new FixedBackOff(100L, 3L);
            factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, backOff));

            return factory;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Инфраструктура
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
    // Бины
    // ─────────────────────────────────────────────────────────

    /**
     * @SpyBean оборачивает реальный DealEventConsumer шпионом.
     * doThrow() переопределяет consume() → кидает RuntimeException на каждой попытке.
     * Остальные методы работают через реальную реализацию.
     */
    @SpyBean
    DealEventConsumer dealEventConsumer;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    // ─────────────────────────────────────────────────────────
    // Тесты
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("consume: при исчерпании retry сообщение уходит в DLT с корректными заголовками")
    void failingConsumer_exhaustsRetries_messagesGoToDlt() {
        // 1. Подписываемся на DLT ДО отправки — не пропустим сообщение
        Consumer<String, byte[]> dltConsumer = createDltConsumer("test-dlt-verify-group");
        embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, KafkaTopics.DEAL_STATUS_CHANGED_DLT);

        // 2. Делаем consume() всегда кидать RuntimeException
        //    Spy перехватывает вызов ДО реального кода → исключение бросается немедленно
        doThrow(new RuntimeException("Simulated poison pill for DLT test"))
                .when(dealEventConsumer).consume(any(), any());

        // 3. Отправляем валидное сообщение
        DealStatusChangedMessage message = new DealStatusChangedMessage(
                777L, DealStatus.NEW, DealStatus.WON,
                Instant.now(), UUID.randomUUID().toString()
        );
        kafkaTemplate.send(KafkaTopics.DEAL_STATUS_CHANGED, "777", message);

        // 4. Ждём DLT-сообщение
        //    FixedBackOff(100ms, 3): 4 attempts × 100ms ≈ 400ms → DLT появится < 2s
        //    Timeout 15s — с запасом на slow CI
        ConsumerRecords<String, byte[]> dltRecords =
                KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(15));
        dltConsumer.close();

        assertThat(dltRecords).as("DLT должен содержать сообщение после исчерпания retry").isNotEmpty();

        ConsumerRecord<String, byte[]> dltRecord = dltRecords.iterator().next();

        // 5. Проверяем заголовки DLT — они позволяют найти оригинал и понять причину сбоя
        String originalTopic = headerAsString(dltRecord, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        assertThat(originalTopic)
                .as("DLT_ORIGINAL_TOPIC должен указывать на исходный топик")
                .isEqualTo(KafkaTopics.DEAL_STATUS_CHANGED);

        String exceptionFqcn = headerAsString(dltRecord, KafkaHeaders.DLT_EXCEPTION_FQCN);
        assertThat(exceptionFqcn)
                .as("DLT_EXCEPTION_FQCN должен содержать класс исключения")
                .contains("RuntimeException");

        String exceptionMessage = headerAsString(dltRecord, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        assertThat(exceptionMessage)
                .as("DLT_EXCEPTION_MESSAGE должен содержать текст исключения")
                .contains("Simulated poison pill");

        // 6. Проверяем что consume() вызывался несколько раз (initial + retries)
        //    FixedBackOff(100, 3) → 4 вызова: 1 initial + 3 retries
        verify(dealEventConsumer, atLeast(2)).consume(any(), any());
    }

    @Test
    @DisplayName("consume: DLT partition key = ключ оригинального сообщения")
    void failingConsumer_dltPreservesOriginalKey() {
        Consumer<String, byte[]> dltConsumer = createDltConsumer("test-dlt-key-group");
        embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, KafkaTopics.DEAL_STATUS_CHANGED_DLT);

        doThrow(new RuntimeException("Key preservation test"))
                .when(dealEventConsumer).consume(any(), any());

        String originalKey = "deal-888";
        kafkaTemplate.send(KafkaTopics.DEAL_STATUS_CHANGED, originalKey,
                new DealStatusChangedMessage(
                        888L, DealStatus.IN_PROGRESS, DealStatus.LOST,
                        Instant.now(), UUID.randomUUID().toString()
                ));

        ConsumerRecords<String, byte[]> dltRecords =
                KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(15));
        dltConsumer.close();

        assertThat(dltRecords).isNotEmpty();

        // DeadLetterPublishingRecoverer сохраняет partition key оригинала
        // Важно для replay: если DLT-consumer переотправляет в оригинальный топик,
        // тот же ключ обеспечит попадание в ту же партицию → ordering сохранится
        ConsumerRecord<String, byte[]> dltRecord = dltRecords.iterator().next();
        assertThat(dltRecord.key()).isEqualTo(originalKey);
    }

    // ─────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────

    /**
     * Consumer для DLT топика с ByteArrayDeserializer.
     *
     * DLT-сообщение может быть невалидным (иначе бы не попало в DLT).
     * byte[] позволяет прочитать его независимо от состояния payload.
     */
    private Consumer<String, byte[]> createDltConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<String, byte[]>(props).createConsumer();
    }

    /**
     * Читает Kafka-заголовок как UTF-8 строку.
     *
     * Все заголовки хранятся как byte[].
     * DeadLetterPublishingRecoverer записывает числовые значения (partition, offset) как строки.
     */
    private String headerAsString(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return "n/a";
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
