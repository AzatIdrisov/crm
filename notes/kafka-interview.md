# Apache Kafka — вопросы с собеседований

---

## 1. Гарантии доставки

Три режима, которые определяются комбинацией настроек producer'а и consumer'а.

### At-most-once (максимум один раз, потеря допустима)

| Сторона | Настройка |
|---|---|
| Producer | `acks=0` или `acks=1` |
| Consumer | коммит offset **до** обработки (авто-коммит в начале) |

**Что происходит при сбое:**
- Producer: брокер не ответил → сообщение потеряно, retry не делается
- Consumer: offset закоммичен → при перезапуске сообщение пропущено

**Когда использовать:** метрики, логи кликов, IoT-телеметрия — потеря части данных приемлема.

---

### At-least-once (минимум один раз, дубликаты возможны)

| Сторона | Настройка |
|---|---|
| Producer | `acks=all` + `retries > 0` |
| Consumer | коммит offset **после** обработки (`enable.auto.commit=false` + ручной `ack.acknowledge()`) |

**Что происходит при сбое:**
- Producer: при timeout → retry → брокер может получить дубликат (PID-дедупликация при `enable.idempotence=true`)
- Consumer: упал после обработки, до `acknowledge()` → при перезапуске то же сообщение придёт снова

**Защита от дублей на стороне consumer:** идемпотентность — `Redis SET NX` по `messageId` (см. `DealEventConsumer`).

**Когда использовать:** бизнес-события, финансовые операции, любые изменения состояния. Consumer **обязан** быть идемпотентным.

> Это наш основной режим: `enable.idempotence=true` + `AckMode.MANUAL_IMMEDIATE`.

---

### Exactly-once (ровно один раз, только внутри Kafka)

| Сторона | Настройка |
|---|---|
| Producer | `transactional.id=<id>` + `enable.idempotence=true` (включается автоматически) |
| Consumer | `isolation.level=read_committed` |

**Как работает:**
1. Producer вызывает `beginTransaction()` → `send()` → `commitTransaction()`
2. Сообщения видны consumer'у **только после** `commitTransaction()`
3. `read_committed` фильтрует сообщения из незакоммиченных/откатанных транзакций
4. `transactional.id` + zombie fencing: брокер убивает старый экземпляр producer'а с тем же id

**Ограничение:** работает только **внутри Kafka** (produce → consume → produce).
Атомарность "записать в PostgreSQL + опубликовать в Kafka" невозможна без **Outbox Pattern**.

**Когда использовать:** Kafka Streams, aggregation пайплайны, дедупликация внутри топиков.

---

## 2. Rebalancing

### Когда происходит rebalance

- Consumer **присоединился** к группе (`JoinGroup`)
- Consumer **покинул** группу (graceful `leaveGroup()` или timeout)
- `max.poll.interval.ms` превышен → брокер считает consumer'а мёртвым
- `session.timeout.ms` превышен → heartbeat не получен
- Изменилось количество **партиций** топика
- Изменился список **подписанных топиков** группы

### Stop-The-World Rebalance (RangeAssignor, RoundRobinAssignor)

```
Consumer A: partition 0, 1     Consumer B: partition 2
                ↓ rebalance начался
STOP: все consumer'ы останавливают обработку
REASSIGN: брокер перераспределяет все партиции заново
RESUME: все consumer'ы возобновляют работу
```

**Проблема:** пока идёт rebalance — весь consumer group стоит. При большом числе consumer'ов и партиций — заметная пауза.

### Incremental Cooperative Rebalance (CooperativeStickyAssignor)

```
Consumer A: partition 0, 1     Consumer B: partition 2
                ↓ Consumer C присоединился
Шаг 1: брокер говорит "отдай partition 1"
Consumer A: отдаёт только partition 1, продолжает читать partition 0
Шаг 2: partition 1 назначается Consumer C
Consumer A и B работают всё время, пауза только у перемещаемых партиций
```

**Включение:**
```java
props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    CooperativeStickyAssignor.class.getName());
```

### max.poll.interval.ms как триггер rebalance

`max.poll.interval.ms` (дефолт: 5 минут) — максимальное время между двумя вызовами `poll()`.

**Сценарий проблемы:**
1. Consumer вызвал `poll()`, получил 100 сообщений
2. Обработка одного сообщения занимает 10 секунд (вызов внешнего API)
3. Через 5 минут брокер думает что consumer умер → rebalance
4. Партиция передана другому consumer'у → те же сообщения будут обработаны снова

**Решения:**
- Уменьшить `max.poll.records` (меньше сообщений за итерацию)
- Увеличить `max.poll.interval.ms` (если обработка действительно долгая)
- Вынести долгую обработку в async и быстро возвращать управление из `poll()`

---

## 3. Offset Management

### Что такое offset

**Offset** — монотонно возрастающий номер записи в партиции (0, 1, 2, ...).
Уникальный идентификатор записи: `(topic, partition, offset)`.

### __consumer_offsets

Служебный топик Kafka. Хранит committed offset для каждой пары `(consumer group, topic, partition)`.

```
key:   (group="crm-notification-group", topic="deal-status-changed", partition=0)
value: offset=42, metadata="", timestamp=...
```

**Retention:** по умолчанию 7 дней. Если группа не читает топик 7 дней → committed offset удаляется.

### auto.offset.reset

Что делать при **первом** старте группы (нет committed offset) или если committed offset устарел (удалён из retention):

| Значение | Поведение |
|---|---|
| `earliest` | начать с самого первого сообщения в топике |
| `latest` (дефолт) | начать с новых сообщений (старые пропускаются) |
| `none` | бросить исключение если нет committed offset |

### Committed offset vs Current offset vs Consumer lag

```
Partition:  [0] [1] [2] [3] [4] [5] [6] [7]
                                 ↑           ↑
                        committed offset=4  latest offset=8

Consumer lag = latest - committed = 8 - 4 = 4 сообщения не обработано
```

**Committed offset** — позиция, до которой группа подтвердила обработку.
**Current offset** — где сейчас находится consumer (может опережать committed).
**Consumer lag** — разница: сколько сообщений ждут обработки. Мониторинг через `kafka-consumer-groups.sh --describe` или Prometheus Kafka Exporter.

---

## 4. Ключевые настройки Producer

### acks — подтверждение записи

| Значение | Поведение | Риск потери |
|---|---|---|
| `0` | не ждать ответа от брокера | высокий |
| `1` | ждать подтверждения лидера партиции | при падении лидера до репликации |
| `all` (`-1`) | ждать подтверждения всех ISR | минимальный |

**ISR (In-Sync Replicas)** — реплики, которые не отстали от лидера больше чем на `replica.lag.time.max.ms`.

### enable.idempotence

`enable.idempotence=true` включает **идемпотентного producer'а**:
- Брокер назначает producer'у `PID` (Producer ID)
- Каждое сообщение получает `sequence number`
- При retry: брокер распознаёт дубликат по `(PID, partition, sequence)` и отбрасывает
- Автоматически устанавливает: `acks=all`, `retries=MAX_VALUE`, `max.in.flight=5`

### linger.ms и batch.size — throughput vs latency

```
linger.ms=5: producer ждёт до 5ms, накапливая сообщения в batch
batch.size=32768: при достижении 32KB — отправляет немедленно

Trade-off:
  linger.ms=0, batch.size=1  → минимальная latency, максимальный overhead (1 сообщение = 1 запрос)
  linger.ms=50, batch.size=65536 → высокий throughput, latency +50ms
```

### max.in.flight.requests.per.connection

Сколько неподтверждённых запросов может быть одновременно на одном TCP-соединении.

| Значение | Без idempotence | С idempotence |
|---|---|---|
| `1` | гарантия порядка, медленно | гарантия порядка |
| `> 1` | при retry порядок может нарушиться | безопасно до 5 |
| `> 5` | быстро, порядок не гарантирован | **запрещено** с idempotence |

### transactional.id

Уникальный идентификатор producer'а для exactly-once:
- Брокер использует для **zombie fencing**: если старый экземпляр с тем же id пытается продолжить транзакцию → `ProducerFencedException`
- В кластере (несколько реплик приложения): `"crm-tx-" + instanceId` иначе реплики убивают транзакции друг друга

### compression.type

| Тип | CPU | Сжатие | Когда |
|---|---|---|---|
| `none` (дефолт) | нет | нет | — |
| `snappy` | низкий | среднее | баланс |
| `lz4` | очень низкий | среднее | высокий throughput |
| `gzip` | высокий | лучшее | хранение важнее CPU |
| `zstd` | средний | отличное | современный выбор |

---

## 5. Ключевые настройки Consumer

### max.poll.records

Максимум записей за один вызов `poll()`. Дефолт: 500.

**Выбор:**
- Маленький (10–50): меньше памяти, быстрее обработка батча, меньше риск exceed `max.poll.interval.ms`
- Большой (500+): выше throughput, но дольше обработка

### max.poll.interval.ms

Максимальное время между двумя `poll()`. Дефолт: 300 000 (5 минут).
При превышении → брокер исключает consumer из группы → rebalance.

**Если обработка медленная:**
```
max.poll.interval.ms = max(обработка 1 сообщения) × max.poll.records + запас
```

### session.timeout.ms

Максимальное время без heartbeat. Дефолт: 45 000 (45 секунд).
Consumer'ы шлют heartbeat в фоновом потоке каждые `heartbeat.interval.ms` (дефолт: 3с).

**Отличие от max.poll.interval.ms:**
- `session.timeout.ms`: JVM жива, но hearbeat не шлёт → сетевая проблема, GC pause, OOM
- `max.poll.interval.ms`: heartbeat идёт, но `poll()` не вызывается → обработка зависла

### fetch.min.bytes

Минимальный размер данных для одного ответа брокера. Дефолт: 1 байт.

При `fetch.min.bytes=1024`: брокер ждёт пока накопится 1KB данных перед ответом.
Trade-off: меньше round-trips (выше throughput) vs больше latency.

### isolation.level

| Значение | Поведение |
|---|---|
| `read_uncommitted` (дефолт) | видит все сообщения, включая из незакоммиченных транзакций |
| `read_committed` | видит только сообщения из закоммиченных транзакций |

**Обязателен** `read_committed` при работе с transactional producer. Без него consumer прочитает сообщение из транзакции, которая потом откатится.

---

## 6. Outbox vs Saga vs CDC — решения проблемы Dual Write

### Проблема Dual Write

В одной бизнес-операции нужно изменить состояние в БД **И** опубликовать событие в Kafka. Эти два действия не атомарны:

```
Сценарий A (потеря события):
  ✓ UPDATE deals SET status = 'WON'  → PostgreSQL commit
  ✗ kafkaTemplate.send()             → Kafka недоступна → событие потеряно

Сценарий B (фантомное событие):
  ✓ kafkaTemplate.send()             → Kafka получила сообщение
  ✗ commit deals                     → БД откатилась → событие без изменения в БД
```

### Сравнительная таблица

| | **Outbox Pattern** | **Saga Pattern** | **CDC (Change Data Capture)** |
|---|---|---|---|
| **Суть** | Event записывается в outbox_messages в той же TX что и бизнес-изменение | Распределённая транзакция через последовательность компенсирующих операций | Debezium читает WAL PostgreSQL и публикует изменения в Kafka |
| **Атомарность** | Гарантируется СУБД (одна транзакция) | Eventual consistency через компенсации | Гарантируется на уровне WAL (каждое изменение гарантированно попадёт в Kafka) |
| **Сложность** | Низкая: `@Scheduled` poller + таблица | Высокая: оркестрация/хореография, компенсирующие транзакции | Средняя: нужен Debezium коннектор, Kafka Connect |
| **Latency** | Небольшая задержка (poller каждые N секунд) | Зависит от цепочки саг | Почти real-time (WAL → Kafka без polling) |
| **Нагрузка на БД** | `SELECT PENDING` каждую секунду | Зависит от реализации | Читает WAL, минимальная нагрузка на таблицы |
| **Инфраструктура** | Только поллер (в приложении) | Оркестратор или event bus | Debezium + Kafka Connect + ZooKeeper |
| **Где применять** | Большинство микросервисных систем | Долгие бизнес-процессы (заказ → склад → доставка → оплата) | Высокая нагрузка, много таблиц, нельзя менять код |
| **At-least-once** | Да (poller повторяет PENDING) | Да (компенсации) | Да (WAL log-based) |
| **Реализация в проекте** | `OutboxMessage` + `OutboxPoller` | — | — |

### Когда что выбирать

**Outbox Pattern** — стандартный выбор для большинства случаев:
- Простая бизнес-операция порождает Kafka-событие
- Команда небольшая, инфраструктура минимальная
- Допустима задержка в 1–5 секунд

**Saga** — когда операция затрагивает несколько микросервисов:
- Заказ: сервис заказов → сервис инвентаря → сервис оплаты
- Нужны компенсирующие транзакции при сбое на любом шаге
- Хореография (события) vs Оркестрация (центральный координатор)

**CDC / Debezium** — когда:
- Нельзя изменить код приложения (legacy)
- Очень высокая нагрузка (тысячи изменений в секунду)
- Нужно реплицировать все изменения из БД в Kafka без выбора
- WAL-уровень даёт near-zero latency

---

## 7. Быстрые ответы на частые вопросы

**Q: Сколько consumer'ов в группе может читать топик с N партициями?**
A: Максимум N. Если consumer'ов > N — лишние будут idle (без назначенных партиций).

**Q: Что такое ISR?**
A: In-Sync Replicas — реплики, которые не отстали от лидера. `acks=all` ждёт подтверждения от всех ISR. `min.insync.replicas=2` — минимум ISR для принятия записи.

**Q: Почему `max.in.flight=5` а не больше с idempotence?**
A: Ограничение протокола. Брокер поддерживает дедупликацию по sequence number только в пределах 5 in-flight запросов. При большем значении гарантии нарушаются.

**Q: Как replay сообщений из DLT?**
A: Прочитать DLT-сообщение, исправить данные/код, опубликовать обратно в оригинальный топик. Kafka позволяет перечитать с любого offset (`auto.offset.reset=earliest` в новой группе).

**Q: Как гарантировать порядок сообщений?**
A: Одинаковый ключ → одна партиция → строгий FIFO-порядок внутри партиции. Между партициями порядок не гарантирован. Наш случай: `dealId` как ключ → все события одной сделки в одну партицию.

**Q: Чем отличается `poll()` от `fetch()`?**
A: `poll()` — API на стороне client библиотеки (проверяет буфер + при необходимости шлёт `FetchRequest`). `FetchRequest` — запрос к брокеру за данными. Один `poll()` может не делать сетевой запрос если данные уже в буфере.

**Q: Что такое Log Compaction?**
A: Режим retention, при котором Kafka хранит только **последнее** сообщение для каждого ключа. Полезно для хранения актуального состояния (changelog топики в Kafka Streams). Настраивается через `cleanup.policy=compact`.

**Q: partition key = null — что происходит?**
A: Round-robin по партициям (sticky partitioner накапливает batch в одну партицию, потом переключается). Порядок между сообщениями без ключа не гарантирован.
