package com.crm.outbox;

import com.crm.kafka.message.DealStatusChangedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис записи сообщений в Outbox-таблицу.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * PROPAGATION.MANDATORY — почему?
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Outbox — это не самостоятельная операция. Запись в outbox_messages имеет
 *  смысл ТОЛЬКО если она выполняется внутри уже открытой транзакции вместе
 *  с бизнес-изменением (например, UPDATE deals SET status = ?).
 *
 *  MANDATORY = "я ТРЕБУЮ существующую транзакцию, сам не открываю".
 *  Если вызвать saveOutboxMessage() без транзакции → IllegalTransactionStateException.
 *
 *  Это страховка от ошибки: разработчик не сможет случайно вызвать метод
 *  вне @Transactional контекста, что привело бы к отдельной записи без
 *  гарантии атомарности с бизнес-изменением — т.е. к той же проблеме dual write.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * СРАВНЕНИЕ PROPAGATION
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  REQUIRED (по умолчанию):
 *   - Есть транзакция → присоединяется. Нет → открывает новую.
 *   - Риск: если вызвать без транзакции — outbox запишется в отдельной транзакции,
 *     бизнес-изменение и outbox больше не атомарны.
 *
 *  MANDATORY:
 *   - Есть транзакция → присоединяется. Нет → throws IllegalTransactionStateException.
 *   - Явно документирует: "этот метод — часть более широкой транзакции".
 *   - Fail-fast: ошибка на этапе разработки, а не в проде.
 *
 *  REQUIRES_NEW:
 *   - Всегда открывает новую транзакцию, приостанавливает текущую.
 *   - НЕ подходит для outbox: outbox должен коммититься ВМЕСТЕ с бизнес-изменением.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * СЕРИАЛИЗАЦИЯ PAYLOAD
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  payload — JSON-строка, а не объект:
 *   - В Kafka уходит именно JSON строка (producerFactory сериализует через Jackson).
 *   - Хранение как TEXT в PostgreSQL позволяет отлаживать без десериализации.
 *   - При schema evolution: старый consumer может прочитать новый формат
 *     (если добавлены только новые поля — backward compatibility).
 *
 *  ObjectMapper из Spring context (не new ObjectMapper()):
 *   - Используем бин, настроенный Spring Boot (JavaTimeModule для Instant, etc.)
 *   - Избегаем дублирования конфигурации сериализации.
 */
@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Сохраняет Outbox-сообщение в рамках ТЕКУЩЕЙ транзакции.
     *
     * Должен вызываться из метода, уже аннотированного @Transactional.
     * Например: DealService.changeStatus() → outboxService.saveOutboxMessage()
     *
     * @param message   DTO события, которое нужно опубликовать в Kafka
     * @throws IllegalTransactionStateException если нет активной транзакции
     * @throws RuntimeException если не удалось сериализовать payload в JSON
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOutboxMessage(DealStatusChangedMessage message) {
        String payload = toJson(message);

        OutboxMessage outbox = OutboxMessage.builder()
                .aggregateType("Deal")
                .aggregateId(message.dealId().toString())
                .eventType("DealStatusChanged")
                .payload(payload)
                .messageId(message.messageId())
                .status(OutboxStatus.PENDING)
                .build();

        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
