package com.crm.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka: два producer'а (idempotent + transactional) и consumer.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ГАРАНТИИ ДОСТАВКИ — три режима:
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  at-most-once (максимум один раз, возможна потеря):
 *    Producer: acks=0 или acks=1
 *    Consumer: коммитить offset ДО обработки (авто-коммит в начале пачки)
 *    Когда использовать: метрики, логи — потеря части данных допустима.
 *
 *  at-least-once (минимум один раз, возможны дубликаты):
 *    Producer: acks=all + retries > 0 (idempotent producer рекомендуется)
 *    Consumer: коммитить offset ПОСЛЕ обработки (ручной коммит / MANUAL_IMMEDIATE)
 *    Когда использовать: большинство бизнес-сценариев. Consumer должен быть идемпотентным.
 *    → Это наш основной режим (idempotent producer + AckMode.MANUAL_IMMEDIATE).
 *
 *  exactly-once (ровно один раз):
 *    Producer: transactional.id + enable.idempotence (включается автоматически)
 *    Consumer: isolation.level=read_committed (не читать незакоммиченные сообщения)
 *    Ограничения: только внутри Kafka (не покрывает внешние системы вроде БД).
 *    Когда использовать: финансовые операции, дедупликация внутри Kafka.
 *    → Демонстрируем через transactionalKafkaTemplate (9.4.2).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ПОЧЕМУ ДВА ProducerFactory:
 * ─────────────────────────────────────────────────────────────────────────────
 *  - Transactional producer не может использоваться без транзакции.
 *    Вызов kafkaTemplate.send() без executeInTransaction() на транзакционном
 *    шаблоне бросит ProducerFencedException.
 *  - Idempotent producer используется для обычной отправки (fire-and-forget с гарантией).
 *  - DeadLetterPublishingRecoverer должен использовать НЕ транзакционный template,
 *    иначе при ошибке в транзакции он не сможет опубликовать в DLT.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ─────────────────────────────────────────────────────────
    // Producer: Idempotent (основной, at-least-once)
    // ─────────────────────────────────────────────────────────

    /**
     * Idempotent producer — защита от дублей на уровне брокера.
     *
     * enable.idempotence = true:
     *   Брокер назначает producer'у PID (Producer ID).
     *   Каждое сообщение получает порядковый номер (sequence number).
     *   При повторной отправке (после timeout/сетевой ошибки) брокер
     *   распознаёт дубликат по (PID, partition, sequence) и отбрасывает его.
     *   Гарантирует exactly-once запись в ОДНУ партицию.
     *
     * acks = all:
     *   Лидер партиции ждёт подтверждения от всех ISR перед ответом producer'у.
     *   Без этого: acks=1 (только лидер) → при падении лидера до репликации = потеря данных.
     *
     * retries = MAX_VALUE:
     *   При enable.idempotence это безопасно — дубликаты на брокере отфильтруются.
     *   Без idempotence бесконечные retry могут привести к дублям.
     *
     * max.in.flight.requests.per.connection = 5:
     *   Максимум 5 неподтверждённых запросов одновременно.
     *   С idempotence допустимо значение до 5 (гарантии упорядоченности сохраняются).
     *   Без idempotence: если > 1, при retry порядок может нарушиться.
     *
     * linger.ms = 5:
     *   Producer ждёт до 5ms накапливая сообщения в batch перед отправкой.
     *   Trade-off: latency +5ms, throughput ↑ (меньше round-trips к брокеру).
     *
     * batch.size = 32768 (32 KB):
     *   Максимальный размер batch. При достижении — отправляется немедленно.
     *   Больший batch → лучший throughput, но больше памяти.
     */
    @Bean
    @Primary
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Idempotence + надёжность
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        // Throughput: накапливаем batch
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ─────────────────────────────────────────────────────────
    // Producer: Transactional (exactly-once внутри Kafka)
    // ─────────────────────────────────────────────────────────

    /**
     * Transactional producer — exactly-once семантика внутри Kafka.
     *
     * transactional.id = "crm-tx-producer":
     *   Уникальный идентификатор producer'а в кластере.
     *   Брокер использует его для:
     *   1. Восстановления незавершённых транзакций после рестарта producer'а (фencing).
     *   2. Zombie fencing: если старый producer с тем же transactional.id попытается
     *      продолжить транзакцию после рестарта — брокер его откажет (ProducerFencedException).
     *
     *   Важно: transactional.id должен быть уникален для каждого экземпляра producer'а.
     *   В кластерных deployment'ах (несколько реплик приложения) добавляют suffix:
     *   "crm-tx-producer-" + instanceId, иначе реплики будут "убивать" транзакции друг друга.
     *
     * enable.idempotence и acks=all включаются автоматически при transactional.id.
     *
     * Ограничения EOS (exactly-once semantics):
     *   - Работает только ВНУТРИ Kafka (produce → consume → produce).
     *   - Не покрывает внешние системы: "записать в БД И в Kafka атомарно" = невозможно
     *     без Outbox Pattern (см. фазу 9.6).
     *   - isolation.level=read_committed на consumer'е обязателен — иначе consumer
     *     увидит сообщения из незакоммиченных/откатанных транзакций.
     */
    @Bean("transactionalProducerFactory")
    public ProducerFactory<String, Object> transactionalProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // transactional.id включает транзакционный режим
        // enable.idempotence=true и acks=all устанавливаются автоматически
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "crm-tx-producer");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("transactionalKafkaTemplate")
    public KafkaTemplate<String, Object> transactionalKafkaTemplate(
            ProducerFactory<String, Object> transactionalProducerFactory) {
        return new KafkaTemplate<>(transactionalProducerFactory);
    }

    // ─────────────────────────────────────────────────────────
    // Consumer
    // ─────────────────────────────────────────────────────────

    /**
     * group.id = "crm-notification-group":
     *   Все consumer'а с одним group.id образуют consumer group.
     *   Kafka распределяет партиции между ними: каждая партиция читается ровно одним consumer'ом.
     *   Два consumer'а в разных group.id читают одни и те же партиции независимо.
     *
     * auto.offset.reset = earliest:
     *   Что делать если для group.id нет сохранённого offset'а (первый запуск).
     *   earliest → начинать с самого начала топика.
     *   latest (дефолт) → начинать с новых сообщений (старые пропускаются).
     *
     * enable.auto.commit = false:
     *   Не коммитить offset автоматически.
     *   По умолчанию Kafka коммитит offset каждые auto.commit.interval.ms=5000ms —
     *   это может привести к at-most-once: offset закоммичен, но обработка ещё не завершена.
     *   С false + AckMode.MANUAL_IMMEDIATE — at-least-once: коммит только после обработки.
     *
     * max.poll.records = 10:
     *   Максимум 10 сообщений за один poll().
     *   Связано с max.poll.interval.ms: если обработка 10 сообщений занимает > 300s
     *   → брокер считает consumer'а мёртвым → rebalance.
     *   Меньше records → меньше нагрузка за раз, но чаще poll → overhead.
     *
     * max.poll.interval.ms = 300000 (5 минут):
     *   Максимальное время между двумя poll(). Если consumer не вызовет poll() за 5 минут
     *   → брокер исключает его из группы и запускает rebalance.
     *   Увеличивать при медленной обработке (например, вызов внешнего API).
     *
     * isolation.level = read_committed:
     *   Consumer видит только сообщения из закоммиченных транзакций.
     *   Без этого: при transactional producer consumer может прочитать сообщение
     *   из транзакции, которая потом откатится (read_uncommitted — дефолт).
     *
     * TRUSTED_PACKAGES:
     *   JsonDeserializer требует явно указать допустимые пакеты для десериализации
     *   во избежание десериализации произвольных классов из заголовка __TypeId__.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "crm-notification-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Доверяем только нашему пакету с DTO сообщений
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.crm.kafka.message");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ─────────────────────────────────────────────────────────
    // Listener Container Factory: retry + DLT
    // ─────────────────────────────────────────────────────────

    /**
     * Настройка обработки ошибок: exponential backoff retry + Dead Letter Topic.
     *
     * DefaultErrorHandler (Spring Kafka 2.8+, замена SeekToCurrentErrorHandler):
     *   При исключении в consumer-методе обрабатывает ошибку согласно BackOff-стратегии.
     *   Между попытками consumer НЕ коммитит offset — сообщение будет обработано снова.
     *
     * ExponentialBackOff (из spring-core, org.springframework.util.backoff):
     *   initialInterval=1000ms, multiplier=2.0, maxInterval=10000ms, maxElapsedTime=30000ms
     *   Задержки между попытками: 1s → 2s → 4s → 8s → 10s → ...
     *   Попытки продолжаются пока суммарное elapsed time < maxElapsedTime (30s).
     *   После исчерпания → передаём сообщение в DeadLetterPublishingRecoverer.
     *
     *   Примечание: у ExponentialBackOff нет setMaxAttempts().
     *   Для точного числа retry используют FixedBackOff(interval, maxAttempts).
     *   Для экспоненциального с ограничением по времени — setMaxElapsedTime.
     *
     *   Зачем exponential, а не fixed:
     *   При временном сбое внешнего сервиса фиксированный retry создаёт шторм запросов.
     *   Экспоненциальный backoff даёт сервису время восстановиться.
     *
     * DeadLetterPublishingRecoverer:
     *   Публикует сообщение в <original-topic>.DLT с заголовками:
     *     DLT_ORIGINAL_TOPIC, DLT_ORIGINAL_PARTITION, DLT_ORIGINAL_OFFSET,
     *     DLT_EXCEPTION_FQCN, DLT_EXCEPTION_MESSAGE, DLT_EXCEPTION_STACKTRACE.
     *   Использует НЕ транзакционный kafkaTemplate — иначе DLT-запись может откатиться
     *   вместе с основной транзакцией при ошибке.
     *
     * AckMode.MANUAL_IMMEDIATE:
     *   Offset коммитится только при вызове Acknowledgment.acknowledge().
     *   В отличие от MANUAL (коммит в конце пачки) — коммит происходит сразу.
     *   Важно: при ошибке НЕ вызываем acknowledge() → после retry или DLT вызывает
     *   acknowledge() сам DefaultErrorHandler.
     *
     * concurrency = 3:
     *   3 потока-listener'а → каждый читает из своей партиции (партиций тоже 3).
     *   Если consumers > partitions — лишние потоки простаивают (idle).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Ручное подтверждение offset'а — основа at-least-once
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 3 потока — по одному на партицию
        factory.setConcurrency(3);

        // DLT через основной (не транзакционный) KafkaTemplate
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // ExponentialBackOff из spring-core.
        // Кол-во попыток не задаётся напрямую — управляется через maxElapsedTime.
        // При 1s → 2s → 4s → 8s суммарное время ≈ 15s + overhead обработки.
        // maxElapsedTime = 30s покрывает ~4 retry и оставляет запас.
        //
        // Для точного числа retry (например ровно 3) — используют FixedBackOff(1000, 3),
        // но теряют экспоненциальное нарастание задержки.
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(10_000L);
        backOff.setMaxElapsedTime(30_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
