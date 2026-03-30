# CRM Pet Project — Задачи

## Фаза 1 — Java Core

- [x] **1.1** Настроить структуру пакетов и главный класс приложения
- [x] **1.2** Создать доменные модели (Customer, Deal, Task, User, Comment)
- [x] **1.3** Создать Enums: DealStatus, TaskPriority, UserRole
- [x] **1.4** Создать базовый generic-класс BaseEntity<ID>
- [x] **1.5** Реализовать Comparable/Comparator для сортировки сделок

## Фаза 2 — Stream API & Functional Programming

- [x] **2.1** Создать DealAnalyticsService (Stream API: filter, map, groupingBy, etc.)
- [x] **2.2** Использовать Optional везде вместо null-проверок в сервисах
- [x] **2.3** Реализовать кастомные Predicate<Customer>, Function, BiFunction в сервисах

## Фаза 3 — Многопоточность

- [x] **3.1** ExecutorService для уведомлений + CompletableFuture для асинхронной загрузки
- [x] **3.2** AtomicInteger, ReentrantLock, ConcurrentHashMap в сервисах
- [x] **3.3** BlockingQueue (producer-consumer) + ScheduledExecutorService

## Фаза 4 — Spring & Spring Boot

- [x] **4.1** Настроить application.yml (dev/prod профили, @ConfigurationProperties)
- [x] **4.2** Реализовать CRUD REST API (Customer, Deal, Task)
- [x] **4.3** Валидация запросов через Bean Validation (@Valid, @NotNull, @NotBlank, @Size, @Email, @Min, @Max, @Pattern)
- [x] **4.4** Кастомные аннотации валидации (@UniqueEmail, @ValidPhone) с собственным ConstraintValidator
- [x] **4.5** @ControllerAdvice: глобальная обработка ошибок (MethodArgumentNotValidException, ConstraintViolationException, кастомные исключения)
- [x] **4.6** Иерархия исключений (CrmException → ResourceNotFoundException, ValidationException, ConflictException) + ErrorResponse DTO
- [x] **4.7** Spring Security + JWT аутентификация и авторизация по ролям
- [x] **4.8** Spring Events: DealStatusChangedEvent + @Async @EventListener

## Фаза 5 — База данных (PostgreSQL)

- [x] **5.1** JPA: связи @OneToMany/@ManyToOne/@ManyToMany, @Version, @Embeddable
- [x] **5.2** Spring Data JPA: Specification, проекции, @Query, @Modifying
- [x] **5.3** Liquibase: миграции для всех таблиц + индексы

## Фаза 6 — Кэширование

- [x] **6.1** Подключить Redis: @Cacheable, @CacheEvict, @CachePut + TTL

## Фаза 7 — Тестирование

### 7.1 Unit-тесты (Mockito) — сервисы без Spring контекста

- [x] **7.1.1** `CustomerServiceTest` — mock `CustomerRepository`:
  - `findById` → возвращает `Optional.of(customer)` если найден, `Optional.empty()` если нет
  - `getByIdOrThrow` → бросает `NoSuchElementException` если не найден
  - `getDisplayName` → возвращает полное имя или `"Unknown Customer"` если пустое
  - `save` → вызывает `repository.save()` и возвращает результат
  - `deleteById` → возвращает `false` если не существует, `true` если удалено
  - `filter(HAS_COMPANY)` → фильтрует только клиентов с компанией
  - `mapToDisplayNames` → маппинг через `TO_DISPLAY_NAME`
  - `GET_CUSTOMER_DEALS` — возвращает только сделки нужного клиента

- [x] **7.1.2** `DealServiceTest` — mock `DealRepository` + `ApplicationEventPublisher`:
  - `findByIdWithDetails` → hit / miss
  - `save` → делегирует в репозиторий
  - `deleteById` → `false` если не существует, `true` если удалено
  - `changeStatus` → бросает `ResourceNotFoundException` если не найден
  - `changeStatus` → меняет статус и публикует `DealStatusChangedEvent`
  - `bulkUpdateStatus` → вызывает `repository.updateStatus()` и возвращает count
  - `search` → вызывает `repository.findAll(spec)` с нужными параметрами

- [x] **7.1.3** `UserServiceTest` — mock `UserRepository` + `PasswordEncoder`:
  - `findByEmail` → нормализует email (trim + toLowerCase) перед поиском
  - `register` → кодирует пароль, сохраняет пользователя с нужными полями
  - `register` → при вызове кэш сбрасывается (проверяем через `@CacheEvict` логику)

### 7.2 @WebMvcTest — HTTP-слой контроллеров

- [x] **7.2.1** `CustomerControllerTest` — mock `CustomerService`:
  - `GET /api/customers` → 200 + список
  - `GET /api/customers/{id}` → 200 если найден, 404 если нет
  - `POST /api/customers` → 201 Created с телом ответа
  - `POST /api/customers` → 400 при невалидном теле (пустое имя, неверный email)
  - `PUT /api/customers/{id}` → 200 если найден, 404 если нет
  - `DELETE /api/customers/{id}` → 204 если удалён, 404 если нет

- [x] **7.2.2** `DealControllerTest` — mock `DealService`:
  - `GET /api/deals/{id}` → 200 если найден, 404 если нет
  - `POST /api/deals` → 201 Created
  - `PATCH /api/deals/{id}/status` → 200 с новым статусом
  - `DELETE /api/deals/{id}` → 204 / 404

### 7.3 @DataJpaTest + Testcontainers — уже реализовано ✅

- [x] `AbstractRepositoryTest` — базовый класс с PostgreSQL Testcontainer
- [x] `EmbeddableAndAuditingTest` — @Embeddable, @CreatedDate, @LastModifiedDate
- [x] `CascadeAndLazyTest` — orphanRemoval, FetchType.LAZY
- [x] `N1QueryTest` — проблема N+1 и JOIN FETCH
- [x] `OptimisticLockingTest` — @Version, ObjectOptimisticLockingFailureException
- [x] `SpecificationTest` — динамическая фильтрация через Specification
- [x] `ProjectionAndModifyingTest` — проекции и @Modifying

## Фаза 8 — Инфраструктура

- [x] **8.1** Docker Compose (PostgreSQL + Redis + App) + Swagger UI

## Фаза 9 — Apache Kafka

### 9.1 Инфраструктура и зависимости

- [x] **9.1.1** Добавить зависимости в `pom.xml`: `spring-kafka` + `spring-kafka-test` (scope=test).
  Концепт: подключение Spring Kafka к Spring Boot 3.2.

- [x] **9.1.2** Добавить сервис `kafka` в `docker-compose.yml`: образ `confluentinc/cp-kafka:7.6.0`
  в KRaft-режиме (без ZooKeeper — `KAFKA_PROCESS_ROLES=broker,controller`,
  `KAFKA_CONTROLLER_QUORUM_VOTERS`, `KAFKA_NODE_ID`). Пробросить порт `9092`.
  Добавить `depends_on: kafka: condition: service_healthy` для сервиса `app`.
  Концепт: **KRaft vs ZooKeeper** — в чём разница, почему KRaft это будущее Kafka.

- [x] **9.1.3** Добавить Kafka-настройки в `application.yml` (раздел `spring.kafka`):
  `bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}`.
  Добавить переменную `KAFKA_BOOTSTRAP: kafka:9092` в `environment` сервиса `app` в `docker-compose.yml`.
  Концепт: externalized configuration для брокера, профили dev/docker.

### 9.2 Конфигурация Kafka (KafkaConfig + KafkaTopics)

- [ ] **9.2.1** Создать `kafka/config/KafkaTopics.java`: объявить `@Bean NewTopic dealStatusChanged()`
  через `TopicBuilder.name("deal-status-changed").partitions(3).replicas(1).build()`.
  Концепт: **TopicBuilder** — декларативное создание топиков через `KafkaAdmin`;
  `partitions=3` позволяет параллельно работать 3 consumer'ам в одной group.id.

- [ ] **9.2.2** Создать `kafka/config/KafkaConfig.java` с конфигурацией producer и consumer:

  **Idempotent producer (non-transactional):**
  ```
  enable.idempotence = true
  acks = all
  retries = Integer.MAX_VALUE
  max.in.flight.requests.per.connection = 5
  linger.ms = 5
  batch.size = 32768
  ```
  Концепт: **idempotent producer** — брокер дедуплицирует дубликаты по (PID, partition, sequence);
  `acks=all` + `retries` = at-least-once гарантия;
  `linger.ms` + `batch.size` = batching для throughput.

  **Transactional producer:**
  ```
  transactional.id = crm-tx-producer
  (enable.idempotence и acks=all включаются автоматически)
  ```
  Концепт: **transactional producer** — `transactional.id` уникально идентифицирует producer;
  позволяет публиковать в несколько партиций атомарно; основа для **exactly-once semantics (EOS)**.

  **Consumer:**
  ```
  group.id = crm-notification-group
  auto.offset.reset = earliest
  enable.auto.commit = false
  max.poll.records = 10
  max.poll.interval.ms = 300000
  isolation.level = read_committed
  ```
  Концепт: `enable.auto.commit=false` — переход на ручной коммит (AckMode.MANUAL);
  `isolation.level=read_committed` — consumer не видит сообщения из незакоммиченных транзакций;
  `max.poll.records` и `max.poll.interval.ms` — защита от rebalance-таймаута при медленной обработке.

- [ ] **9.2.3** Добавить `KafkaListenerContainerFactory` с `DefaultErrorHandler` +
  `ExponentialBackOffWithMaxRetries(3)` и `DeadLetterPublishingRecoverer`.
  Концепт: **retry с exponential backoff** — при exception consumer делает 3 попытки
  с удвоением паузы (1s → 2s → 4s), затем отправляет сообщение в топик `deal-status-changed.DLT`;
  **Dead Letter Topic** — изоляция "ядовитых" сообщений (poison pill) от нормального потока.

### 9.3 DTO сообщения

- [ ] **9.3.1** Создать `kafka/message/DealStatusChangedMessage.java` — Java record:
  ```java
  record DealStatusChangedMessage(
      Long dealId,
      DealStatus oldStatus,
      DealStatus newStatus,
      Instant occurredAt,
      String messageId   // UUID для идемпотентной обработки на стороне consumer
  )
  ```
  Концепт: **message schema** — record immutable по природе; `messageId` — ключ идемпотентности;
  `occurredAt` vs `processedAt` — event time vs processing time; отличие Kafka DTO от Spring ApplicationEvent.

### 9.4 Producer

- [ ] **9.4.1** Создать `kafka/producer/DealEventProducer.java`. Метод `send()` использует
  `kafkaTemplate.send("deal-status-changed", msg.dealId().toString(), msg)` —
  `dealId` как ключ партицонирования гарантирует, что все события одной сделки
  попадают в одну партицию и обрабатываются **в порядке отправки**.
  Добавить callback через `CompletableFuture` для логирования ошибок доставки.
  Концепт: **partition key для ordering**, **fire-and-forget vs. synchronous send**,
  `RecordMetadata` (топик, партиция, offset) из callback.

- [ ] **9.4.2** Добавить метод `sendTransactional()` через `kafkaTemplate.executeInTransaction(...)`.
  Концепт: **transactional send** — `beginTransaction()` / `commitTransaction()` / `abortTransaction()`;
  как `@Transactional` на бине с `transactional.id` интегрируется с `KafkaTransactionManager`;
  отличие от обычного `KafkaTemplate` без `transactional.id`.

- [ ] **9.4.3** Обновить `DealService.changeStatus()`: добавить вызов `dealEventProducer.send(...)`
  рядом с `publisher.publishEvent(...)`. Добавить комментарий: почему прямой вызов
  `kafkaTemplate.send()` внутри `@Transactional` создаёт проблему **dual write**
  (DB commit + Kafka publish не атомарны) — и что Outbox Pattern решает это.

### 9.5 Consumer

- [ ] **9.5.1** Создать `kafka/consumer/DealEventConsumer.java`:
  ```java
  @KafkaListener(
      topics = "deal-status-changed",
      groupId = "crm-notification-group",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void consume(ConsumerRecord<String, DealStatusChangedMessage> record, Acknowledgment ack)
  ```
  Внутри: обработка → `ack.acknowledge()`. Если обработка упала — не вызываем `ack`,
  `DefaultErrorHandler` сделает retry.
  Концепт: **AckMode.MANUAL_IMMEDIATE** — offset коммитится сразу после `ack.acknowledge()`;
  **at-least-once** — при падении до `ack` сообщение придёт повторно → нужна идемпотентность.

- [ ] **9.5.2** Добавить проверку идемпотентности через Redis:
  ```java
  String key = "kafka:processed:" + record.value().messageId();
  if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
      ack.acknowledge(); return;
  }
  // ... обработка ...
  redisTemplate.opsForValue().set(key, "1", Duration.ofHours(24));
  ack.acknowledge();
  ```
  Концепт: **idempotent consumer** — защита от дублей при rebalance или retry;
  Redis как хранилище processed message IDs; TTL — компромисс между памятью и окном дедупликации.

- [ ] **9.5.3** Добавить логирование metadata: `topic`, `partition`, `offset`, `key`.
  Концепт: **ConsumerRecord metadata** — offset как позиция в партиции;
  как `__consumer_offsets` хранит committed offsets; consumer lag.

- [ ] **9.5.4** Создать `kafka/consumer/DlqConsumer.java` с `@KafkaListener(topics = "deal-status-changed.DLT")`.
  Логировать заголовки: `DLT_EXCEPTION_CAUSE_FQCN`, `DLT_ORIGINAL_TOPIC`,
  `DLT_ORIGINAL_PARTITION`, `DLT_ORIGINAL_OFFSET`.
  Концепт: **Dead Letter Topic headers** — метаданные первоначальной ошибки;
  ручной replay DLT-сообщений; мониторинг и алерты на DLT-lag.

### 9.6 Outbox Pattern

- [ ] **9.6.1** Создать `outbox/OutboxMessage.java` — `@Entity @Table(name = "outbox_messages")`:
  поля: `aggregateType`, `aggregateId`, `eventType`, `payload` (JSON), `messageId` (UUID),
  `status` (PENDING / SENT / FAILED), `createdAt`, `processedAt`.
  Концепт: **Outbox Pattern** — событие сохраняется в той же транзакции что и бизнес-данные;
  UPDATE deals + INSERT outbox_messages атомарны в рамках одной PostgreSQL-транзакции;
  устраняет dual write.

- [ ] **9.6.2** Добавить Liquibase changeset `003-outbox.sql`: таблица `outbox_messages` +
  индекс `idx_outbox_status` по колонке `status`.
  Концепт: индекс на статус для быстрого выбора PENDING-записей; без него poller деградирует при росте таблицы.

- [ ] **9.6.3** Создать `outbox/OutboxRepository.java`:
  ```java
  List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
  ```
  Концепт: ограничение батча poller'а; сортировка по `createdAt` сохраняет порядок событий.

- [ ] **9.6.4** Создать `outbox/OutboxService.java` с методом
  `saveOutboxMessage(DealStatusChangedMessage msg)` и `@Transactional(propagation = MANDATORY)`.
  Концепт: **MANDATORY propagation** — гарантирует что запись в outbox будет в транзакции вызывающего;
  если вызвать без транзакции — `IllegalTransactionStateException`;
  сравнение: REQUIRES_NEW vs MANDATORY для Outbox.

- [ ] **9.6.5** Обновить `DealService.changeStatus()`: вызвать `outboxService.saveOutboxMessage(...)`
  внутри существующей `@Transactional`. Убрать прямой `dealEventProducer.send()` — теперь
  публикация в Kafka происходит только через poller.
  Концепт: полная реализация Outbox — единственная точка публикации, транзакционная гарантия.

- [ ] **9.6.6** Создать `outbox/OutboxPoller.java` с `@Scheduled(fixedDelay = 1000)`:
  читать 100 PENDING-записей → публиковать в Kafka → обновлять статус в SENT/FAILED.
  Добавить `@EnableScheduling` в конфигурацию.
  Концепт: **Polling Outbox** — периодически читает PENDING, публикует, помечает SENT;
  `fixedDelay` vs `fixedRate` при медленной обработке;
  poller может опубликовать дубликаты (at-least-once) → consumer должен быть идемпотентен;
  альтернатива polling'у — **CDC (Change Data Capture)** с Debezium (упомянуть в комментарии).

### 9.7 Тесты с @EmbeddedKafka

- [ ] **9.7.1** Создать `KafkaProducerConsumerIntegrationTest` — `@SpringBootTest` + `@EmbeddedKafka`.
  Тест: `DealEventProducer.send()` → `KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))`
  → проверить payload сообщения.
  Концепт: **`@EmbeddedKafka`** — in-process брокер без Docker;
  vs Testcontainers Kafka — trade-off скорость vs реализм;
  `@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")`.

- [ ] **9.7.2** Создать `OutboxPollerIntegrationTest` — `@SpringBootTest` + `@EmbeddedKafka`
  + PostgreSQL Testcontainer (переиспользовать подход из `AbstractRepositoryTest`).
  Сценарий: `DealService.changeStatus()` → проверить PENDING запись в БД →
  ждать срабатывания poller'а через Awaitility (`await().atMost(5, SECONDS)`) →
  проверить статус SENT → проверить сообщение в Kafka.
  Концепт: **интеграционный тест Outbox end-to-end**;
  **Awaitility** для async assertions вместо `Thread.sleep`.

- [ ] **9.7.3** Создать `DlqIntegrationTest`: consumer намеренно бросает исключение →
  после 3 retry сообщение попадает в `deal-status-changed.DLT` →
  проверить заголовок `KafkaHeaders.DLT_ORIGINAL_TOPIC`.
  Концепт: тестирование retry-логики и DLT; BackOff-последовательность;
  `ArgumentCaptor` для проверки количества попыток.

### 9.8 Конспект: вопросы с собеседований

- [ ] **9.8.1** Создать `notes/kafka-interview.md` с разделами:
  - **Гарантии доставки**: at-most-once / at-least-once / exactly-once — условия и настройки для каждого
  - **Rebalancing**: когда происходит; `RangeAssignor` vs `CooperativeStickyAssignor`;
    Stop-The-World vs incremental cooperative rebalance; `max.poll.interval.ms` как триггер
  - **Offset management**: `__consumer_offsets`; `auto.offset.reset=earliest/latest`;
    committed offset vs current offset; consumer lag
  - **Ключевые настройки producer**: `acks`, `linger.ms`, `batch.size`, `compression.type`,
    `enable.idempotence`, `transactional.id`, `max.in.flight.requests.per.connection`
  - **Ключевые настройки consumer**: `max.poll.records`, `max.poll.interval.ms`,
    `session.timeout.ms`, `fetch.min.bytes`, `isolation.level`
  - **Outbox vs Saga vs CDC**: сравнительная таблица подходов к dual write проблеме
