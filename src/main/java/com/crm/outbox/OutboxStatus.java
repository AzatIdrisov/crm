package com.crm.outbox;

/**
 * Статус сообщения в Outbox-таблице.
 *
 *  PENDING → SENT:   poller успешно опубликовал сообщение в Kafka
 *  PENDING → FAILED: poller не смог опубликовать (Kafka недоступна)
 *
 *  FAILED-записи можно:
 *   - повторить вручную (UPDATE status='PENDING')
 *   - игнорировать и алертить (мониторинг count(status='FAILED'))
 *
 *  Переходы SENT → PENDING не нужны: после SENT сообщение уже у брокера,
 *  повторная отправка создаст дубликат (но consumer идемпотентен — это ОК).
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
