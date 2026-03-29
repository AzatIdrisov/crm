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

- [ ] **8.1** Docker Compose (PostgreSQL + Redis + App) + Swagger UI
