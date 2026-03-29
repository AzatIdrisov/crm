# Кэширование в Spring Boot + Redis

## Оглавление
1. [Зачем нужен кэш](#1-зачем-нужен-кэш)
2. [Как работает кэш в Spring](#2-как-работает-кэш-в-spring)
3. [Аннотации кэширования](#3-аннотации-кэширования)
4. [Redis как хранилище кэша](#4-redis-как-хранилище-кэша)
5. [Настройка RedisCacheManager](#5-настройка-rediscachemanager)
6. [Сериализация объектов](#6-сериализация-объектов)
7. [Проблема с Hibernate Lazy Loading](#7-проблема-с-hibernate-lazy-loading)
8. [Стратегии инвалидации кэша](#8-стратегии-инвалидации-кэша)
9. [Кэширование Optional](#9-кэширование-optional)
10. [Подводные камни](#10-подводные-камни)
11. [Шпаргалка по аннотациям](#11-шпаргалка-по-аннотациям)

---

## 1. Зачем нужен кэш

Кэш — промежуточный слой хранения, который отдаёт часто запрашиваемые данные **без обращения к БД**.

```
Запрос → Spring → [Кэш] → (hit) → ответ
                         → (miss) → БД → сохранить в кэш → ответ
```

**Когда кэшировать:**
- Данные читаются часто, меняются редко (справочники, профили пользователей)
- Запрос к БД дорогой (JOIN FETCH, агрегации)
- Снижение нагрузки на БД при большом количестве запросов

**Когда НЕ кэшировать:**
- Данные меняются при каждом запросе
- Данные уникальны для каждого пользователя (сессионные данные)
- Небольшой объём данных, БД отвечает мгновенно

---

## 2. Как работает кэш в Spring

Spring Cache — **абстракция** над любым хранилищем кэша (Redis, Caffeine, EhCache, HashMap).

Работает через **AOP-прокси**: Spring оборачивает бин и перехватывает вызовы аннотированных методов.

```java
// Без кэша — каждый вызов идёт в БД:
userService.findByEmail("test@mail.com"); // → SELECT ...
userService.findByEmail("test@mail.com"); // → SELECT ...

// С @Cacheable — второй вызов из кэша:
userService.findByEmail("test@mail.com"); // → SELECT ... (miss, сохранили)
userService.findByEmail("test@mail.com"); // → кэш (hit, БД не трогали)
```

**Включение кэша:**
```java
@SpringBootApplication
@EnableCaching  // обязательно!
public class CrmApplication { }
```

---

## 3. Аннотации кэширования

### @Cacheable — читать из кэша

```java
@Cacheable(value = "users", key = "#email")
public Optional<User> findByEmail(String email) {
    return userRepository.findByEmailValue(email);
}
```

- При вызове Spring проверяет кэш `users` по ключу `email`
- **Cache hit** — возвращает из кэша, метод не выполняется
- **Cache miss** — выполняет метод, сохраняет результат в кэш

**Параметры:**
| Параметр | Описание | Пример |
|----------|----------|--------|
| `value` | Имя кэша | `"users"` |
| `key` | SpEL-выражение для ключа | `"#id"`, `"#email"` |
| `condition` | Кэшировать если true | `"#id > 0"` |
| `unless` | НЕ кэшировать если true | `"#result == null"` |

---

### @CachePut — обновить кэш

```java
@CachePut(value = "customers", key = "#result.id")
@Transactional
public Customer save(Customer customer) {
    return customerRepository.save(customer);
}
```

- **Всегда выполняет метод** (в отличие от @Cacheable)
- Сохраняет результат в кэш
- Используется для обновления кэша после записи в БД

**Разница @Cacheable vs @CachePut:**
```
@Cacheable:  [кэш есть?] → да → вернуть кэш (метод НЕ вызван)
                         → нет → вызвать метод → сохранить в кэш

@CachePut:   вызвать метод → сохранить в кэш (всегда)
```

---

### @CacheEvict — удалить из кэша

```java
// Удалить конкретную запись
@CacheEvict(value = "deals", key = "#id")
@Transactional
public boolean deleteById(Long id) { ... }

// Удалить все записи из кэша
@CacheEvict(value = "users", allEntries = true)
@Transactional
public User register(...) { ... }
```

**Параметры:**
| Параметр | Описание |
|----------|----------|
| `key` | Ключ конкретной записи |
| `allEntries = true` | Очистить весь кэш |
| `beforeInvocation = true` | Удалить ДО выполнения метода (по умолчанию — после) |

**Когда нужен `beforeInvocation = true`:**
```java
// Если метод может выбросить исключение — кэш всё равно очистится
@CacheEvict(value = "deals", key = "#id", beforeInvocation = true)
public void riskyDelete(Long id) { ... }
```

---

### @Caching — комбинация аннотаций

```java
@Caching(
    evict = {
        @CacheEvict(value = "deals", key = "#result.id"),
        @CacheEvict(value = "customers", key = "#result.customer.id")
    }
)
public Deal save(Deal deal) { ... }
```

---

### @CacheConfig — настройки на уровне класса

```java
@Service
@CacheConfig(cacheNames = "deals")  // value по умолчанию для всего класса
public class DealService {

    @Cacheable(key = "#id")  // не нужно писать value = "deals"
    public Optional<Deal> findById(Long id) { ... }
}
```

---

## 4. Redis как хранилище кэша

**Redis** — in-memory хранилище типа key-value. Данные хранятся в RAM → очень быстрый доступ.

```yaml
# application.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cache:
    type: redis
```

**Зависимости в pom.xml:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## 5. Настройка RedisCacheManager

По умолчанию Spring использует Java-сериализацию для Redis. Лучше настроить JSON:

```java
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory,
                                          ObjectMapper objectMapper) {
        // Сериализатор значений — JSON с информацией о типе (@class)
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // Базовая конфигурация для всех кэшей
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();  // не хранить null

        // Разные TTL для разных кэшей
        Map<String, RedisCacheConfiguration> configs = Map.of(
            CacheNames.USERS,     defaultConfig.entryTtl(Duration.ofMinutes(30)),
            CacheNames.CUSTOMERS, defaultConfig.entryTtl(Duration.ofMinutes(10)),
            CacheNames.DEALS,     defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
```

**TTL (Time To Live)** — время жизни записи в кэше. После истечения Redis удаляет запись автоматически.

---

## 6. Сериализация объектов

Чтобы объект можно было сохранить в Redis (JSON), он должен быть десериализуем обратно.

### GenericJackson2JsonRedisSerializer

Сохраняет в JSON с полем `@class`:
```json
{
  "@class": "com.crm.model.User",
  "id": 1,
  "email": { "@class": "com.crm.model.value.Email", "value": "admin@crm.com" },
  "createdAt": "2024-01-15T10:30:00"
}
```

Поле `@class` позволяет Jackson десериализовать обратно в правильный тип.

### Настройка ObjectMapper

```java
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Поддержка Java 8 Time (LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Поддержка Hibernate-прокси (см. раздел 7)
        Hibernate6Module hibernateModule = new Hibernate6Module();
        hibernateModule.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        hibernateModule.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        mapper.registerModule(hibernateModule);

        return mapper;
    }
}
```

---

## 7. Проблема с Hibernate Lazy Loading

**Проблема:** JPA-сущности часто имеют связи с `FetchType.LAZY`.

```java
@ManyToOne(fetch = FetchType.LAZY)
private Customer customer;  // не загружен сразу — это Hibernate-прокси
```

Когда Spring пытается сохранить объект в Redis **после закрытия Hibernate-сессии**:
- Jackson видит прокси-объект
- Пытается вызвать `customer.getName()` для сериализации
- Hibernate-сессия закрыта → `LazyInitializationException`

**Решение 1 — JOIN FETCH:**
```java
// Кэшировать метод, который загружает все связи явно
@Cacheable(value = "deals", key = "#id")
public Optional<Deal> findByIdWithDetails(Long id) {
    return dealRepository.findByIdWithDetails(id);  // использует JOIN FETCH
}

// В репозитории:
@Query("SELECT d FROM Deal d LEFT JOIN FETCH d.customer LEFT JOIN FETCH d.assignedTo WHERE d.id = :id")
Optional<Deal> findByIdWithDetails(Long id);
```

**Решение 2 — Hibernate6Module:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-hibernate6</artifactId>
</dependency>
```

```java
Hibernate6Module module = new Hibernate6Module();
// НЕ загружать lazy при сериализации (не трогать незагруженные связи)
module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
// Для незагруженных lazy — сериализовать только id (не null, не падать)
module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
```

Теперь незагруженный `customer` сериализуется как `{ "id": 5 }` вместо исключения.

---

## 8. Стратегии инвалидации кэша

### Cache-Aside (Lazy Loading) — наш подход

```
Чтение:  проверить кэш → miss → читать БД → записать в кэш → вернуть
Запись:  записать в БД → удалить/обновить кэш
```

```java
@Cacheable(value = "deals", key = "#id")   // читаем из кэша если есть
public Optional<Deal> findByIdWithDetails(Long id) { ... }

@CacheEvict(value = "deals", key = "#id")  // инвалидируем при изменении
public Deal changeStatus(Long id, DealStatus status) { ... }
```

### Write-Through

```
Запись: записать в БД → сразу обновить кэш (@CachePut)
```

```java
@CachePut(value = "customers", key = "#result.id")
public Customer save(Customer customer) { ... }
```

### Когда использовать allEntries = true

Если после операции **невозможно определить, какие записи изменились**:
```java
// register может влиять на поиск любого пользователя — сбрасываем всё
@CacheEvict(value = "users", allEntries = true)
public User register(...) { ... }
```

---

## 9. Кэширование Optional

Проблема: если кэшировать `Optional.empty()`, следующий запрос тоже получит "не найдено" из кэша, даже если запись уже создана.

```java
// НЕПРАВИЛЬНО — кэшируем пустой Optional
@Cacheable(value = "users", key = "#email")
public Optional<User> findByEmail(String email) { ... }

// ПРАВИЛЬНО — не кэшируем пустой результат
@Cacheable(value = "users", key = "#email", unless = "#result.isEmpty()")
public Optional<User> findByEmail(String email) { ... }
```

**SpEL для unless:**
```java
unless = "#result == null"           // для простых объектов
unless = "#result.isEmpty()"         // для Optional
unless = "#result.size() == 0"       // для Collection
```

---

## 10. Подводные камни

### 1. Self-invocation не работает

```java
@Service
public class DealService {

    public void processDeals() {
        findByIdWithDetails(1L);  // НЕ пройдёт через прокси → кэш не сработает!
    }

    @Cacheable(value = "deals", key = "#id")
    public Optional<Deal> findByIdWithDetails(Long id) { ... }
}
```

**Решение:** Внедрить сам себя через `@Autowired` или вынести метод в отдельный бин.

---

### 2. @Transactional + @Cacheable

```java
// Кэш сохраняется ПОСЛЕ транзакции (commit)
// Если транзакция откатилась — кэш не будет обновлён
@CachePut(value = "customers", key = "#result.id")
@Transactional
public Customer save(Customer customer) { ... }
```

Порядок применения: `@Transactional` обёртывает метод, `@CachePut` срабатывает после возврата из метода (уже после commit).

---

### 3. Ключ должен быть уникальным между кэшами

```java
// Разные кэши — разные пространства имён, ключи не пересекаются
@Cacheable(value = "customers", key = "#id")  // Redis key: "customers::1"
@Cacheable(value = "deals",     key = "#id")  // Redis key: "deals::1"
```

---

### 4. @CachePut и @Cacheable хранят разные типы

```java
@Cacheable(value = "customers", key = "#id")
public Optional<Customer> findById(Long id) { ... }
// Кэш хранит: Optional<Customer>

@CachePut(value = "customers", key = "#result.id")
public Customer save(Customer customer) { ... }
// Кэш хранит: Customer (не Optional!)
```

При следующем вызове `findById` — ключ уже есть в кэше, но тип не совпадает → **ClassCastException**.

**Решение:** использовать `@CacheEvict` на save вместо `@CachePut`, чтобы следующий `findById` загрузил свежие данные из БД и сохранил как `Optional<Customer>`.

---

### 5. Бесконечный TTL = утечка памяти

Всегда задавайте TTL. Redis может занять всю RAM если записи не истекают.

```java
// Задать TTL глобально в RedisCacheManager
defaultConfig.entryTtl(Duration.ofMinutes(10))
```

---

## 11. Шпаргалка по аннотациям

```java
// Читать из кэша, при промахе — выполнить метод и сохранить
@Cacheable(value = "имя_кэша", key = "#параметр", unless = "#result == null")

// Всегда выполнить метод и обновить кэш (для write-through)
@CachePut(value = "имя_кэша", key = "#result.id")

// Удалить запись из кэша
@CacheEvict(value = "имя_кэша", key = "#id")

// Удалить весь кэш
@CacheEvict(value = "имя_кэша", allEntries = true)

// Удалить ДО выполнения метода
@CacheEvict(value = "имя_кэша", key = "#id", beforeInvocation = true)

// Несколько операций с кэшем на одном методе
@Caching(
    evict = { @CacheEvict(value = "cache1", key = "#id"),
              @CacheEvict(value = "cache2", allEntries = true) }
)

// SpEL — полезные выражения для key/condition/unless
// #paramName           — значение параметра метода
// #result              — возвращаемое значение
// #result.id           — поле возвращаемого объекта
// #result.isEmpty()    — для Optional/Collection
// T(com.x.CacheNames).USERS — обращение к константе класса
```

---

## Используемые зависимости в проекте

```xml
<!-- Redis + Spring Cache -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Jackson + Hibernate lazy-proxy serialization -->
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-hibernate6</artifactId>
</dependency>
```
