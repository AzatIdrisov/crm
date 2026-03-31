# CRM Pet Project — Контекст сессии

## Цель проекта
Подготовка к Java-собеседованиям через практику на pet-проекте CRM системы.
Стек: Java 17, Spring Boot, PostgreSQL, Redis.

---

## Что сделано

### Фаза 1 — Java Core ✅
- `BaseEntity<ID>` — generic базовый класс (ID — параметр типа, заменяется при наследовании)
- Доменные модели: `Customer`, `Deal`, `Task`, `User`, `Comment`
- Enums: `DealStatus`, `TaskPriority`, `UserRole`
- `Deal` реализует `Comparable<Deal>` (сортировка по убыванию суммы) + 3 статических `Comparator`
- Value Objects (Java Records): `Email`, `PhoneNumber`, `Money` в пакете `com.crm.model.value`

### Фаза 2 — Stream API & Functional Programming ✅

#### DealAnalyticsService (2.1) — все методы реализованы и покрыты тестами
| Метод | Stream-операция |
|---|---|
| `filterByStatus` | `filter` + enum сравнение через `==` |
| `getTitles` | `map` + method reference |
| `groupByStatus` | `Collectors.groupingBy` |
| `countByStatus` | `groupingBy` + `Collectors.counting()` |
| `groupByManager` | `groupingBy` + filter null |
| `totalAmount` | `map` + `filter(nonNull)` + `reduce(Money::add)` |
| `totalAmountByStatus` | `groupingBy` + `Collectors.summingDouble` |
| `getTopDeals` | `sorted()` (Comparable) + `limit` |
| `findLargest` | `max(Comparator.comparing(...))` |
| `hasWonDeals` | `anyMatch` |
| `allDealsAssigned` | `allMatch` |
| `summaryReport` | `map` + `Collectors.joining("\n")` |
| `getDistinctManagers` | `flatMap` + `distinct` |
| `indexById` | `filter(id != null)` + `Collectors.toMap` |

#### CustomerService (2.2 + 2.3) — Optional, Predicate, Function, BiFunction
- `findById` / `findByEmail` — возвращают `Optional`
- `getByIdOrThrow` — `orElseThrow`
- `getDisplayName` — цепочка `map → map → filter → orElse`
- `HAS_COMPANY`, `HAS_EMAIL`, `HAS_PHONE` — статические `Predicate<Customer>`
- `fromCompany(String)` — фабричный метод предиката
- `hasContactInfo()` — композиция `HAS_EMAIL.and(HAS_PHONE)`
- `filter(Predicate)` — применить предикат через stream
- `TO_DISPLAY_NAME` — `Function<Customer, String>`
- `TO_UPPER_DISPLAY` — `TO_DISPLAY_NAME.andThen(String::toUpperCase)`
- `mapToDisplayNames` — `stream().map(TO_DISPLAY_NAME)`
- `GET_CUSTOMER_DEALS` — `BiFunction<Customer, List<Deal>, List<Deal>>`
- `dealsByStatus()` — `BiFunction<Customer, DealStatus, Predicate<Deal>>`

### Фаза 3 — Многопоточность (в процессе)
Созданы каркасы классов с TODO:

#### NotificationService — ExecutorService
- `3.1.1` `sendNotification` — `submit(Runnable)`, fire and forget
- `3.1.2` `sendBatch` — пакетная отправка
- `3.1.3` `sendWithResult` — `submit(Callable)` → `Future<String>`
- `3.1.4` `waitForResult` — `Future.get()` с обработкой ошибок
- `3.1.5` `shutdown` — graceful shutdown

#### DealLoaderService — CompletableFuture
- `3.1.6` `loadDealAsync` — `supplyAsync()`
- `3.1.7` `loadDealAsString` — `thenApply()`
- `3.1.8` `loadTwoDeals` — `thenCombine()`
- `3.1.9` `loadWithFallback` — `exceptionally()`
- `3.1.10` `loadAll` — `allOf()`

---

## Важные решения и фиксы

### Lombok @SuperBuilder
**Проблема:** `@Builder` не включает поля родительского класса (`id` из `BaseEntity`).
**Решение:** Заменить `@Builder` на `@SuperBuilder` во всех классах цепочки.
**Нюанс:** `@AllArgsConstructor` конфликтует с `@SuperBuilder` — убрали его везде.
`BaseEntity` нужен `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.

### Deal.compareTo vs max()
**Проблема:** `compareTo` в Deal инвертирован (убывание), поэтому `max(naturalOrder())`
возвращал сделку с наименьшей суммой.
**Решение:** В `findLargest` использовать явный компаратор:
```java
.max(Comparator.comparing(d -> d.getAmount().amount()))
```

### @EnableCaching без CacheManager
**Проблема:** `@EnableCaching` требует `CacheManager`, но Redis ещё не подключён.
**Решение:** Убрать `@EnableCaching` до Фазы 6.

---

## Структура пакетов
```
com.crm
├── model
│   ├── BaseEntity.java
│   ├── Customer.java
│   ├── Deal.java
│   ├── Task.java
│   ├── User.java
│   ├── Comment.java
│   ├── enums
│   │   ├── DealStatus.java
│   │   ├── TaskPriority.java
│   │   └── UserRole.java
│   └── value
│       ├── Email.java
│       ├── PhoneNumber.java
│       └── Money.java
└── service
    ├── DealAnalyticsService.java
    ├── CustomerService.java
    ├── NotificationService.java
    └── DealLoaderService.java
```

---

## Темы для собеседования — пройдено
- Generics (`BaseEntity<ID>`)
- Comparable / Comparator
- Value Objects / Records
- Stream API (все основные операции)
- Optional (цепочки, orElse, orElseThrow)
- Functional interfaces (Predicate, Function, BiFunction, композиция)
- Enum сравнение (`==` vs `equals`)
- Lombok: `@SuperBuilder`, наследование

## Темы — в процессе
- ExecutorService, Future, Callable
- CompletableFuture
- AtomicInteger, ReentrantLock, ConcurrentHashMap
- BlockingQueue, ScheduledExecutorService
