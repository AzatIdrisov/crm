package com.crm.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Декларативное создание Kafka-топиков через KafkaAdmin.
 *
 * Ключевые концепции:
 *
 *  - TopicBuilder / KafkaAdmin:
 *    При старте приложения Spring Boot автоконфигурирует KafkaAdmin,
 *    который при наличии @Bean NewTopic создаёт топики в брокере (если их нет).
 *    Если топик уже существует — ничего не делает.
 *    Это идемпотентно: безопасно запускать при каждом рестарте.
 *
 *  - partitions (партиции):
 *    Единица параллелизма. Один consumer-thread может читать из одной партиции.
 *    partitions=3 → максимум 3 consumer'а в одной group.id могут работать параллельно.
 *    Увеличить партиции можно, уменьшить — нельзя (без пересоздания топика).
 *
 *  - replicas (реплики):
 *    Сколько брокеров хранят копию партиции.
 *    replicas=1: нет отказоустойчивости (ОК для одного брокера в dev/local).
 *    replicas=3: стандарт для prod — при падении одного брокера данные не теряются.
 *    replicas не может быть больше числа брокеров в кластере.
 *
 *  - ISR (In-Sync Replicas):
 *    Реплики, которые синхронизированы с лидером.
 *    При acks=all producer ждёт подтверждения от всех ISR.
 *    min.insync.replicas (настройка брокера/топика) — минимум ISR для записи.
 *    Типичная формула: replicas=3, min.insync.replicas=2 →
 *    можно потерять 1 брокер без потери возможности записи.
 *
 *  - DLT (Dead Letter Topic):
 *    Конвенция Spring Kafka: <original-topic>.DLT
 *    Туда попадают сообщения, которые не удалось обработать после всех retry.
 *    DLT имеет 1 партицию (не нужен параллелизм — обрабатывается вручную/по алертам).
 */
@Configuration
public class KafkaTopics {

    // Константы имён топиков — избегаем magic strings в @KafkaListener
    public static final String DEAL_STATUS_CHANGED     = "deal-status-changed";
    public static final String DEAL_STATUS_CHANGED_DLT = "deal-status-changed.DLT";

    /**
     * Основной топик: события изменения статуса сделки.
     *
     * 3 партиции → 3 consumer'а могут читать параллельно.
     * Ключ партицонирования = dealId → все события одной сделки идут в одну партицию
     * и обрабатываются строго по порядку (ordering гарантия в рамках партиции).
     */
    @Bean
    public NewTopic dealStatusChanged() {
        return TopicBuilder.name(DEAL_STATUS_CHANGED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead Letter Topic для "ядовитых" сообщений (poison pill).
     *
     * 1 партиция: сообщения в DLT обрабатываются вручную или по алертам,
     * параллелизм не нужен.
     *
     * Примечание: Spring Kafka DeadLetterPublishingRecoverer мог бы создать DLT
     * автоматически через auto.create.topics.enable=true на брокере, но явное
     * объявление даёт контроль над количеством партиций и репликами.
     */
    @Bean
    public NewTopic dealStatusChangedDlt() {
        return TopicBuilder.name(DEAL_STATUS_CHANGED_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
