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
- [ ] **3.2** AtomicInteger, ReentrantLock, ConcurrentHashMap в сервисах
- [ ] **3.3** BlockingQueue (producer-consumer) + ScheduledExecutorService

## Фаза 4 — Spring & Spring Boot

- [ ] **4.1** Настроить application.yml (dev/prod профили, @ConfigurationProperties)
- [ ] **4.2** Реализовать CRUD REST API (Customer, Deal, Task)
- [ ] **4.3** Валидация запросов через Bean Validation (@Valid, @NotNull, @NotBlank, @Size, @Email, @Min, @Max, @Pattern)
- [ ] **4.4** Кастомные аннотации валидации (@UniqueEmail, @ValidPhone) с собственным ConstraintValidator
- [ ] **4.5** @ControllerAdvice: глобальная обработка ошибок (MethodArgumentNotValidException, ConstraintViolationException, кастомные исключения)
- [ ] **4.6** Иерархия исключений (CrmException → ResourceNotFoundException, ValidationException, ConflictException) + ErrorResponse DTO
- [ ] **4.7** Spring Security + JWT аутентификация и авторизация по ролям
- [ ] **4.8** Spring Events: DealStatusChangedEvent + @Async @EventListener

## Фаза 5 — База данных (PostgreSQL)

- [ ] **5.1** JPA: связи @OneToMany/@ManyToOne/@ManyToMany, @Version, @Embeddable
- [ ] **5.2** Spring Data JPA: Specification, проекции, @Query, @Modifying
- [ ] **5.3** Liquibase: миграции для всех таблиц + индексы

## Фаза 6 — Кэширование

- [ ] **6.1** Подключить Redis: @Cacheable, @CacheEvict, @CachePut + TTL

## Фаза 7 — Тестирование

- [ ] **7.1** Unit-тесты (Mockito), @WebMvcTest, @DataJpaTest, Testcontainers

## Фаза 8 — Инфраструктура

- [ ] **8.1** Docker Compose (PostgreSQL + Redis + App) + Swagger UI
