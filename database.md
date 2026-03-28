# База данных: PostgreSQL + JPA/Hibernate + Liquibase

## Содержание
1. [JPA: архитектура и аннотации](#1-jpa-архитектура-и-аннотации)
2. [Связи между сущностями](#2-связи-между-сущностями)
3. [N+1 проблема](#3-n1-проблема)
4. [Транзакции](#4-транзакции)
5. [Оптимистичная блокировка (@Version)](#5-оптимистичная-блокировка-version)
6. [@Embeddable и Value Objects](#6-embeddable-и-value-objects)
7. [Spring Data JPA: репозитории](#7-spring-data-jpa-репозитории)
8. [Specification (динамические запросы)](#8-specification-динамические-запросы)
9. [Проекции (Projections)](#9-проекции-projections)
10. [Liquibase: управление миграциями](#10-liquibase-управление-миграциями)
11. [Индексы в PostgreSQL](#11-индексы-в-postgresql)
12. [Частые ошибки и антипаттерны](#12-частые-ошибки-и-антипаттерны)

---

## 1. JPA: архитектура и аннотации

### Что такое JPA и Hibernate
- **JPA** (Jakarta Persistence API) — спецификация интерфейсов для ORM в Java.
- **Hibernate** — реализация JPA. Spring Boot 3.x использует Hibernate 6.x.
- **Spring Data JPA** — обёртка над JPA: генерирует репозитории, derived queries, Specification.

### @MappedSuperclass vs @Entity vs @Inheritance

| Аннотация | Своя таблица | Наследуют поля | Когда использовать |
|---|---|---|---|
| `@MappedSuperclass` | Нет | Да (в таблицы наследников) | Общие поля (id, createdAt) |
| `@Entity` | Да | — | Каждая конкретная сущность |
| `@Inheritance(TABLE_PER_CLASS)` | Да, отдельная | Да (копируются) | Иерархия с разными полями |
| `@Inheritance(SINGLE_TABLE)` | Да, одна | Да (в одной таблице) | Иерархия с похожими полями |

В нашем проекте: `BaseEntity<ID>` — `@MappedSuperclass`, все конкретные классы — `@Entity`.

### Ключевые аннотации

```java
@Entity                          // Класс маппится в таблицу
@Table(name = "deals")           // Явное имя таблицы (иначе — имя класса)
@Id                              // Первичный ключ
@GeneratedValue(strategy = GenerationType.IDENTITY)  // BIGSERIAL в PostgreSQL
@Column(name = "first_name", nullable = false, length = 100)
@Enumerated(EnumType.STRING)     // Enum как VARCHAR, не как int-индекс!
@Version                         // Оптимистичная блокировка
@Embedded                        // Встроить @Embeddable объект как набор колонок
@AttributeOverride(name = "value", column = @Column(name = "email"))
```

### GenerationType: IDENTITY vs SEQUENCE vs AUTO

| Стратегия | Поведение | PostgreSQL | Проблема |
|---|---|---|---|
| `IDENTITY` | Полагается на BIGSERIAL/автоинкремент БД | `bigserial` | Батчинг INSERT невозможен — каждая строка требует отдельного round-trip для получения id |
| `SEQUENCE` | Hibernate берёт id из sequence заранее | `CREATE SEQUENCE` | Батчинг работает (берём N id сразу через allocationSize) |
| `AUTO` | Hibernate выбирает сам | Зависит от версии | Непредсказуемо — лучше явно |

**Рекомендация**: для высоконагруженных INSERT — `SEQUENCE` с `allocationSize = 50`.
Для простых проектов — `IDENTITY` достаточно.

---

## 2. Связи между сущностями

### Схема связей проекта

```
User ←── assigned_to ──── Deal ────── customer ──→ Customer
          (ManyToOne)     |  |          (ManyToOne)
                          |  └── tasks (OneToMany) ──→ Task
                          └── comments (OneToMany) ──→ Comment ──→ User (author)
```

### @ManyToOne — "многие к одному"

```java
// В Deal: много сделок → один клиент
@ManyToOne(fetch = FetchType.LAZY)   // ОБЯЗАТЕЛЬНО LAZY!
@JoinColumn(name = "customer_id")    // FK-колонка в таблице deals
private Customer customer;
```

**Дефолт для @ManyToOne — EAGER!** Это ловушка. Без явного `LAZY` Hibernate загружает
связанный объект сразу при загрузке Deal. Если загружаете список сделок — для каждой
делается SELECT customer. Это N+1.

### @OneToMany — "один ко многим"

```java
// В Customer: один клиент → много сделок
@OneToMany(
    mappedBy = "customer",        // поле в Deal, которое является владельцем связи
    cascade = CascadeType.ALL,    // операции каскадируются на дочерние объекты
    orphanRemoval = true,         // удалить Deal из списка → DELETE из БД
    fetch = FetchType.LAZY        // дефолт для @OneToMany, но лучше явно
)
private List<Deal> deals = new ArrayList<>();
```

**Владелец связи** — та сторона, где физически находится FK-колонка. В `deals` таблице
есть `customer_id` → `Deal` является владельцем. `mappedBy` у `Customer` говорит:
«я обратная сторона, смотри на поле `customer` в Deal».

### Инициализация коллекций
```java
// ПРАВИЛЬНО: инициализировать при объявлении
private List<Deal> deals = new ArrayList<>();

// НЕПРАВИЛЬНО: null
private List<Deal> deals;
// customer.getDeals().add(deal) → NullPointerException для нового объекта
```

### CascadeType: когда что использовать

| CascadeType | Значение | Типичный сценарий |
|---|---|---|
| `PERSIST` | сохранить дочерние при сохранении родителя | новый Comment при создании Deal |
| `MERGE` | обновить дочерние при обновлении родителя | обновление через detached entity |
| `REMOVE` | удалить дочерние при удалении родителя | удаление Customer → удаление его Deal |
| `ALL` | все из выше | стандарт для агрегатов |
| `REFRESH` | обновить из БД при refresh родителя | редко нужен |
| `DETACH` | отцепить дочерние вместе с родителем | редко нужен |

**Осторожно с `CascadeType.REMOVE`**: если Customer удаляется, удалятся ВСЕ его
сделки. Это может быть нежелательным поведением. В продакшне часто используют
`ON DELETE SET NULL` в DDL вместо каскадного удаления.

---

## 3. N+1 проблема

### Что это такое

```
Загрузить 100 сделок → 1 SELECT
Для каждой сделки загрузить клиента → 100 SELECT
Итого: 101 запрос = N+1
```

Возникает когда:
- `@ManyToOne` без LAZY (дефолт EAGER)
- Обращение к LAZY коллекции в цикле за пределами транзакции

### Как обнаружить

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

Или подключить **Hibernate Statistics** / **p6spy** / **datasource-proxy**.

### Решение 1: JOIN FETCH в JPQL

```java
@Query("""
    SELECT d FROM Deal d
    LEFT JOIN FETCH d.customer
    LEFT JOIN FETCH d.assignedTo
    WHERE d.id = :id
    """)
Optional<Deal> findByIdWithDetails(@Param("id") Long id);
```

**Ограничение**: нельзя делать JOIN FETCH для нескольких коллекций одновременно —
Hibernate бросает `MultipleBagFetchException`. Решение: одна коллекция через JOIN FETCH,
остальные — через `@EntityGraph` или отдельные запросы.

### Решение 2: @EntityGraph

```java
@EntityGraph(attributePaths = {"customer", "assignedTo", "tasks"})
Optional<Deal> findById(Long id);
```

Генерирует LEFT JOIN FETCH. Аналогично JOIN FETCH, но декларативно.

### Решение 3: default_batch_fetch_size

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 25
```

Вместо N отдельных `SELECT customer WHERE id = ?` Hibernate генерирует:
`SELECT customer WHERE id IN (?, ?, ..., ?)` батчами по 25.
Не так эффективно как JOIN FETCH, но работает автоматически без изменения запросов.

### Решение 4: Проекции (только нужные данные)

```java
List<DealSummary> findAllProjectedBy();
```

Если нужны только id + title + status — не загружаем связанные сущности вообще.

### Решение 5: LAZY + Open Session in View = АНТИПАТТЕРН

```yaml
# По умолчанию в Spring Boot включён:
spring.jpa.open-in-view: true  # НЕ ДЕЛАТЬ В ПРОДЕ
```

Open Session in View держит persistence context открытым до конца HTTP-запроса.
Это позволяет LAZY-загрузку в View (шаблоны). Проблема: неконтролируемые SQL-запросы
в рандомных местах, транзакция держится весь request → блокировки → деградация под нагрузкой.

**Всегда отключать**: `spring.jpa.open-in-view: false`

---

## 4. Транзакции

### @Transactional: как работает

Spring создаёт прокси вокруг `@Transactional`-метода. При вызове прокси:
1. Открывает транзакцию (BEGIN)
2. Вызывает реальный метод
3. При успехе — COMMIT; при RuntimeException — ROLLBACK

```java
@Transactional(readOnly = true)  // Класс: все методы read-only по умолчанию
public class DealService {

    @Transactional                // Метод: переопределяет на read-write
    public Deal changeStatus(Long id, DealStatus newStatus) {
        Deal deal = dealRepository.findById(id).orElseThrow(...);
        deal.setStatus(newStatus);
        // save() НЕ нужен! Hibernate отслеживает изменения сущности (dirty checking)
        // и генерирует UPDATE при коммите транзакции автоматически.
        return deal;
    }
}
```

### readOnly = true: что даёт

- Hibernate пропускает **dirty checking** (не проверяет что изменилось)
- PostgreSQL может направить запрос на **read-only реплику**
- HikariCP может оптимизировать соединение
- Явно сигнализирует намерение — не будет случайного UPDATE

### Propagation: вложенные транзакции

| Propagation | Поведение |
|---|---|
| `REQUIRED` (дефолт) | Присоединиться к существующей или создать новую |
| `REQUIRES_NEW` | Всегда создать новую, приостановить текущую |
| `NESTED` | Savepoint в рамках текущей транзакции |
| `SUPPORTS` | Использовать текущую если есть, иначе без транзакции |
| `NOT_SUPPORTED` | Всегда без транзакции, приостановить текущую |
| `MANDATORY` | Только если уже есть транзакция, иначе exception |
| `NEVER` | Если есть транзакция — exception |

### Типичная ошибка: self-invocation

```java
@Service
public class DealService {

    public void process() {
        this.changeStatus(1L, WON);  // НЕ РАБОТАЕТ — вызов через this, не через прокси
    }

    @Transactional
    public void changeStatus(Long id, DealStatus status) { ... }
}
```

Решение: вынести метод в отдельный бин или инжектировать себя через `ApplicationContext`.

### @Transactional и checked exceptions

По умолчанию ROLLBACK только на `RuntimeException` и `Error`.
На checked exceptions — COMMIT! Это неочевидно.

```java
@Transactional(rollbackFor = Exception.class)  // явно указать
public void doSomething() throws IOException { ... }
```

---

## 5. Оптимистичная блокировка (@Version)

### Проблема без блокировки

```
Thread 1: читает Deal(id=1, status=NEW, version=0)
Thread 2: читает Deal(id=1, status=NEW, version=0)
Thread 1: UPDATE deals SET status=WON, version=1 WHERE id=1 AND version=0 → OK
Thread 2: UPDATE deals SET status=LOST, version=1 WHERE id=1 AND version=0 → 0 строк!
// Hibernate видит 0 обновлённых строк → OptimisticLockException
// Без @Version: Thread 2 перезапишет изменения Thread 1 (Lost Update)
```

### Как работает @Version

```java
@Version
private Long version;
```

Hibernate при UPDATE добавляет: `WHERE id = ? AND version = ?`
Если version в БД уже изменилась (другой поток успел) → UPDATE обновит 0 строк →
Hibernate бросает `OptimisticLockException` → Spring преобразует в `ObjectOptimisticLockingFailureException`.

### Как обрабатывать

```java
try {
    deal = dealService.changeStatus(id, newStatus);
} catch (ObjectOptimisticLockingFailureException e) {
    // Повторить запрос / сообщить пользователю о конфликте
}
```

### Оптимистичная vs Пессимистичная блокировка

| | Оптимистичная | Пессимистичная |
|---|---|---|
| Механизм | @Version, проверка при коммите | SELECT FOR UPDATE (блокировка строки) |
| Нагрузка | Низкая при редких конфликтах | Всегда блокирует |
| Deadlock | Невозможен | Возможен |
| Когда | Чтения >> Записи, редкие конфликты | Частые конфликты, критична консистентность |

```java
// Пессимистичная блокировка в репозитории:
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM Deal d WHERE d.id = :id")
Optional<Deal> findByIdForUpdate(@Param("id") Long id);
// Генерирует: SELECT ... FROM deals WHERE id = ? FOR UPDATE
```

---

## 6. @Embeddable и Value Objects

### Зачем @Embeddable

Хранить Value Object как набор колонок в родительской таблице (без отдельной таблицы).

```java
@Embeddable
public record Money(BigDecimal amount, Currency currency) { ... }

@Entity
public class Deal extends BaseEntity<Long> {
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount",   column = @Column(name = "amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "currency"))
    })
    private Money amount;
}
// В таблице deals: две колонки — amount и currency
```

### Records как @Embeddable (Hibernate 6.2+)

Hibernate 6.2 добавил поддержку Java Records как `@Embeddable`. Использует
**канонический конструктор** для гидрации (создания объекта из ResultSet).
Setter-ы не нужны — Records иммутабельны.

### @AttributeOverride: зачем нужен

Без `@AttributeOverride` Hibernate использует имена компонентов record'а как имена колонок.
`Email.value` → колонка `value`. Если таких embedded два в одном классе — конфликт имён.

```java
@Embedded
@AttributeOverride(name = "value", column = @Column(name = "email"))
private Email email;

@Embedded
@AttributeOverride(name = "value", column = @Column(name = "phone"))
private PhoneNumber phone;
```

### AttributeConverter: для одиночных нестандартных типов

```java
@Converter(autoApply = true)    // применяется ко всем Currency полям автоматически
public class CurrencyConverter implements AttributeConverter<Currency, String> {
    @Override
    public String convertToDatabaseColumn(Currency currency) {
        return currency == null ? null : currency.getCurrencyCode(); // "RUB"
    }
    @Override
    public Currency convertToEntityAttribute(String code) {
        return code == null ? null : Currency.getInstance(code);
    }
}
```

Когда использовать Converter вместо Embeddable:
- Тип маппится в ОДНУ колонку → Converter
- Тип маппится в НЕСКОЛЬКО колонок → Embeddable

---

## 7. Spring Data JPA: репозитории

### Иерархия интерфейсов

```
Repository (маркер)
  └── CrudRepository<T, ID>         save, findById, findAll, delete, count
        └── PagingAndSortingRepository  findAll(Pageable), findAll(Sort)
              └── JpaRepository<T, ID>  flush, saveAndFlush, deleteInBatch, getReferenceById
                    └── JpaSpecificationExecutor<T>  findAll(Specification)
```

### Derived queries (производные запросы)

Spring Data генерирует JPQL по имени метода:

```java
findByStatus(DealStatus status)
// → WHERE d.status = :status

findByCustomerId(Long id)
// → WHERE d.customer.id = :id

findByAssignedToIdAndStatus(Long userId, DealStatus status)
// → WHERE d.assignedTo.id = :userId AND d.status = :status

findByTitleContainingIgnoreCase(String keyword)
// → WHERE LOWER(d.title) LIKE %keyword%

findByCreatedAtBetween(LocalDateTime from, LocalDateTime to)
// → WHERE d.createdAt BETWEEN :from AND :to

countByStatus(DealStatus status)
// → SELECT COUNT(*) WHERE d.status = :status

existsByEmailValue(String value)
// → SELECT COUNT(*) > 0 WHERE d.email.value = :value (embedded!)
```

### @Query: JPQL и Native SQL

```java
// JPQL — работает с именами классов и полей Java, не с таблицами
@Query("SELECT d FROM Deal d WHERE d.assignedTo.id = :userId AND d.status = :status")
List<Deal> findByAssignedUserAndStatus(@Param("userId") Long userId, @Param("status") DealStatus status);

// Native SQL — работает напрямую с таблицами
@Query(value = "SELECT * FROM deals WHERE status = ?1 LIMIT ?2", nativeQuery = true)
List<Deal> findTopByStatusNative(String status, int limit);
```

### @Modifying: UPDATE и DELETE

```java
@Modifying(clearAutomatically = true)   // очищает persistence context после DML
@Transactional
@Query("UPDATE Deal d SET d.status = :status WHERE d.id = :id")
int updateStatus(@Param("id") Long id, @Param("status") DealStatus status);
```

**clearAutomatically = true**: после @Modifying запроса кэш первого уровня (persistence context)
будет содержать устаревшие данные. `clearAutomatically` очищает его автоматически.
Без него: загруженный объект Deal в памяти показывает старый статус, в БД — уже новый.

### Пагинация и сортировка

```java
Page<Deal> findByStatus(DealStatus status, Pageable pageable);

// Использование:
Pageable page = PageRequest.of(0, 20, Sort.by("createdAt").descending());
Page<Deal> result = dealRepository.findByStatus(NEW, page);
result.getContent();      // текущая страница
result.getTotalElements(); // всего записей
result.getTotalPages();    // всего страниц
```

---

## 8. Specification (динамические запросы)

### Проблема без Specification

```java
// Антипаттерн: множество методов для каждой комбинации фильтров
findByStatus(status)
findByStatusAndCustomerId(status, customerId)
findByStatusAndCustomerIdAndAssignedToId(...)
// При 5 фильтрах: 2^5 = 32 метода
```

### Решение: Specification

```java
// Каждый фильтр — отдельная Specification
public static Specification<Deal> hasStatus(DealStatus status) {
    return (root, query, cb) ->
        status == null ? null : cb.equal(root.get("status"), status);
}

// Комбинация через .and() / .or()
Specification<Deal> spec = Specification
    .where(DealSpecification.hasStatus(status))
    .and(DealSpecification.hasCustomer(customerId))
    .and(DealSpecification.amountGreaterThan(minAmount));

dealRepository.findAll(spec);
```

Null-safe: если параметр null → возвращается null → Spring Data игнорирует этот критерий.

### Доступ к embedded полю в Specification

```java
// Money — @Embedded, у него есть компонент "amount"
root.get("amount").get("amount")  // Deal.amount.amount (BigDecimal)
root.get("customer").get("id")    // Deal.customer.id (через @ManyToOne)
```

---

## 9. Проекции (Projections)

### Зачем нужны

Иногда нужны не все поля сущности. Загрузка полного `Deal` со всеми связями ради
вывода только `id + title + status` — расточительно.

### Interface-based projection

```java
public interface DealSummary {
    Long getId();
    String getTitle();
    DealStatus getStatus();
    MoneyView getAmount();        // вложенная проекция для @Embedded

    interface MoneyView {
        BigDecimal getAmount();
    }
}

// Spring Data генерирует SELECT только нужных колонок:
List<DealSummary> findAllProjectedBy();
```

### DTO projection (через @Value или конструктор)

```java
public record DealDto(Long id, String title, DealStatus status) {}

@Query("SELECT new com.crm.dto.DealDto(d.id, d.title, d.status) FROM Deal d")
List<DealDto> findAllAsDto();
```

### Сравнение подходов

| Подход | SELECT | Производительность | Гибкость |
|---|---|---|---|
| Entity | все колонки + JOIN | базовая | полная |
| Interface projection | только нужные | лучше | высокая |
| DTO projection | только нужные | наилучшая | средняя |

---

## 10. Liquibase: управление миграциями

### Почему не `ddl-auto: create/update`

- `create` — **уничтожает данные** при каждом рестарте. Допустимо только в тестах.
- `update` — не удаляет старые колонки, не создаёт индексы, не управляет данными.
  Непредсказуем при сложных изменениях. Не подходит для прода.
- `validate` — только проверяет соответствие. Полезно в CI.
- `none` (используем) — Hibernate не трогает схему. Liquibase управляет всем.

### Структура Liquibase

```
db/changelog/
├── db.changelog-master.yaml    ← точка входа, перечисляет changesets
├── 001-init-schema.sql         ← создание таблиц
└── 002-indexes.sql             ← индексы
```

### Принципы changeset

```sql
--changeset author:changeset-id
ALTER TABLE deals ADD COLUMN priority VARCHAR(50);
--rollback ALTER TABLE deals DROP COLUMN priority;
```

- Каждый changeset применяется **ровно один раз** (Liquibase хранит чексумму в DATABASECHANGELOG)
- **Никогда не редактировать** применённый changeset — чексумма изменится → ошибка при запуске
- Если нужно исправить — создать новый changeset
- Rollback секция позволяет откатить: `liquibase rollbackCount 1`

### DATABASECHANGELOG таблица

Liquibase автоматически создаёт служебную таблицу:

```sql
SELECT id, author, filename, dateexecuted, orderexecuted, md5sum, description
FROM databasechangelog
ORDER BY orderexecuted;
```

### Форматы changesets

| Формат | Когда использовать |
|---|---|
| SQL | DDL-операции, сложные запросы, понятен DBA |
| YAML | Кросс-СУБД изменения (Liquibase генерирует нужный диалект) |
| XML | Старые проекты, legasy |

---

## 11. Индексы в PostgreSQL

### Когда создавать индекс

- FK-колонки (customer_id, deal_id) — JOIN всегда будет делать Index Scan
- Колонки в WHERE-условиях (status, completed)
- Колонки ORDER BY при частых сортировках
- Колонки UNIQUE (PostgreSQL создаёт автоматически при UNIQUE CONSTRAINT)

### Когда НЕ создавать

- Таблицы с частыми bulk INSERT/UPDATE — каждый индекс замедляет запись
- Низкоселективные колонки (boolean, малое количество уникальных значений)
- Колонки, которые редко используются в запросах

### Составной индекс: порядок важен

```sql
CREATE INDEX idx_deals_assigned_status ON deals (assigned_to_id, status);
```

PostgreSQL может использовать этот индекс для:
- `WHERE assigned_to_id = ?`
- `WHERE assigned_to_id = ? AND status = ?`

Но **не** для:
- `WHERE status = ?` (нет leftmost prefix)

### Частичный индекс

```sql
CREATE INDEX idx_tasks_due_date ON tasks (due_date) WHERE completed = FALSE;
```

Индекс строится только для невыполненных задач — меньше размер, быстрее обновление.

### EXPLAIN ANALYZE: проверить использование индекса

```sql
EXPLAIN ANALYZE
SELECT * FROM deals WHERE assigned_to_id = 1 AND status = 'NEW';
```

Ищем в выводе:
- `Index Scan using idx_deals_assigned_status` — индекс используется ✓
- `Seq Scan on deals` — полное сканирование, индекс не используется ✗

---

## 12. Частые ошибки и антипаттерны

### 1. LazyInitializationException

```
org.hibernate.LazyInitializationException: could not initialize proxy - no Session
```

**Причина**: обращение к LAZY коллекции за пределами транзакции.

```java
// НЕПРАВИЛЬНО:
@Transactional
public Customer findCustomer(Long id) {
    return customerRepository.findById(id).orElseThrow();
    // транзакция закрыта при выходе из метода
}
// В контроллере:
customer.getDeals();  // ← LazyInitializationException!

// ПРАВИЛЬНО:
@Transactional(readOnly = true)
public Customer findCustomerWithDeals(Long id) {
    Customer c = customerRepository.findById(id).orElseThrow();
    c.getDeals().size(); // инициализировать в рамках транзакции
    return c;
}
// Или использовать JOIN FETCH / @EntityGraph
```

### 2. MultipleBagFetchException

```
cannot simultaneously fetch multiple bags
```

**Причина**: JOIN FETCH для нескольких коллекций (`List`) одновременно.

```java
// НЕПРАВИЛЬНО:
@Query("""
    SELECT d FROM Deal d
    LEFT JOIN FETCH d.tasks
    LEFT JOIN FETCH d.comments  -- ← нельзя для двух List одновременно
    """)

// ПРАВИЛЬНО: одна коллекция через JOIN FETCH, вторая через @EntityGraph или Set
// Или: Set вместо List (Hibernate допускает несколько SET FETCH)
```

### 3. @Enumerated(ORDINAL) — скрытая бомба

```java
// НЕПРАВИЛЬНО:
@Enumerated(EnumType.ORDINAL)  // или вообще без @Enumerated (дефолт ORDINAL)
private DealStatus status;
// В БД: 0 = NEW, 1 = IN_PROGRESS, 2 = WON, 3 = LOST, 4 = ON_HOLD

// Добавили новый статус PENDING между NEW и IN_PROGRESS:
// Теперь 1 = PENDING, а старый 1 (IN_PROGRESS) читается как PENDING!
// Все данные в БД сломаны.

// ПРАВИЛЬНО:
@Enumerated(EnumType.STRING)  // хранить "NEW", "WON" и т.д.
```

### 4. @Transactional на private методе

```java
@Transactional  // не работает!
private void doSomething() { ... }
```

Spring AOP-прокси не перехватывает private методы. `@Transactional` молча игнорируется.

### 5. Изменение applied changeset

```
Validation Failed: ... was: ... but is now: ...
```

Liquibase проверяет чексумму каждого changeset при запуске. Редактирование
уже применённого SQL вызовет ошибку у всех. Создавайте новый changeset.

### 6. fetch = EAGER для @OneToMany

```java
// АНТИПАТТЕРН:
@OneToMany(fetch = FetchType.EAGER)  // загружает ВСЕ сделки при загрузке клиента
private List<Deal> deals;
// + Spring Data не умеет корректно делать pagination с EAGER коллекциями
// (HibernateJpaDialect: HHH90003004: firstResult/maxResults specified with collection fetch)
```

### 7. save() внутри @Transactional не нужен для изменения

```java
@Transactional
public Deal changeStatus(Long id, DealStatus status) {
    Deal deal = dealRepository.findById(id).orElseThrow();
    deal.setStatus(status);
    // dealRepository.save(deal);  ← ЛИШНЕЕ
    // Hibernate dirty-checking обнаружит изменение при commit автоматически
    return deal;
}
```

### 8. N+1 при toString() / equals() / hashCode()

Lombok `@ToString` по умолчанию включает все поля, включая LAZY коллекции:

```java
@ToString  // вызовет загрузку deals, tasks, comments!
public class Deal { ... }

// ПРАВИЛЬНО: явно исключить коллекции
@ToString(exclude = {"tasks", "comments", "customer"})
// Или использовать @ToString.Exclude на отдельных полях
```

Аналогично: `@EqualsAndHashCode` на JPA-сущностях может вызвать N+1 и проблемы
с Hibernate proxy. Рекомендация: использовать только `@Id` в equals/hashCode.

---

## Итого: что спросят на собеседовании

**N+1**: что это, как обнаружить (`show-sql: true`), как решить (JOIN FETCH, @EntityGraph, batch_fetch_size).

**EAGER vs LAZY**: дефолты (@ManyToOne = EAGER, @OneToMany = LAZY), почему EAGER опасен.

**@Transactional**: как работает (AOP-прокси), readOnly, propagation, self-invocation ловушка, rollback на checked exceptions.

**@Version**: оптимистичная блокировка, когда OptimisticLockException, разница с SELECT FOR UPDATE.

**Liquibase vs ddl-auto**: почему ddl-auto: update нельзя в проде, как работает Liquibase, нельзя менять применённые changesets.

**@Enumerated(STRING)**: почему не ORDINAL.

**Specification**: для чего, как combines через .and()/.or(), null-safe.

**@Embeddable**: что такое, @AttributeOverride, Converter для нестандартных типов.
