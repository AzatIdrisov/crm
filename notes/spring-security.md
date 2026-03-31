# Spring Security - подробные заметки для собеседования

## 1. Что такое Spring Security
Spring Security - это набор фильтров, который стоит перед твоими контроллерами и:
- определяет, кто сделал запрос (authentication),
- решает, что этому пользователю разрешено (authorization).

Вся логика проходит через **SecurityFilterChain**, а не напрямую в контроллеры.

## 2. Authentication vs Authorization (простыми словами)
- **Authentication** - "кто ты?" (пароль, токен, сертификат).
- **Authorization** - "что тебе можно?" (роль ADMIN, право READ и т.д.).

## 3. Как проходит запрос (пошагово)
1) Запрос входит в `SecurityFilterChain`.
2) Фильтр ищет данные аутентификации (форма логина, Basic, JWT и т.д.).
3) Если нашёл - создаёт `Authentication` и отправляет в `AuthenticationManager`.
4) `AuthenticationManager` выбирает подходящий `AuthenticationProvider`.
5) При успехе объект `Authentication` кладётся в `SecurityContext`.
6) Далее проверяются правила доступа, и только потом вызывается контроллер.

## 4. SecurityFilterChain: главный вход
С версии Spring Security 6 больше нет `WebSecurityConfigurerAdapter`.
Вместо него: один или несколько бинов `SecurityFilterChain`:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN","MANAGER","VIEWER")
            .requestMatchers("/api/**").hasAnyRole("ADMIN","MANAGER")
            .anyRequest().authenticated()
        )
        .build();
}
```

Важно:
- порядок фильтров имеет значение,
- для JWT фильтр должен стоять **до** `UsernamePasswordAuthenticationFilter`.

## 5. AuthenticationManager и AuthenticationProvider
`AuthenticationManager` - это входная точка, но реальную проверку делает `AuthenticationProvider`.

Часто используется `DaoAuthenticationProvider`:
- вытаскивает пользователя через `UserDetailsService`,
- сравнивает пароли через `PasswordEncoder`.

## 6. UserDetails и UserDetailsService
Spring не знает твою доменную модель, поэтому работает через интерфейсы:
- `UserDetails` - обёртка над пользователем (username, password, authorities).
- `UserDetailsService` - загрузка пользователя по username.

Обычно:
```java
class CrmUserDetails implements UserDetails {
    private final User user;
    public String getUsername() { return user.getEmail().toString(); }
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
}
```

Нюанс: `hasRole("ADMIN")` ожидает `ROLE_ADMIN`.

## 7. PasswordEncoder
Никогда не хранить пароли в открытом виде.
Используем BCrypt:
```java
new BCryptPasswordEncoder()
```
В реальном проекте часто применяют `DelegatingPasswordEncoder`, чтобы поддерживать разные алгоритмы.

## 8. Авторизация: роли и права
Основные методы:
- `hasRole("ADMIN")` - автоматически добавляет префикс ROLE_.
- `hasAuthority("ROLE_ADMIN")` - проверяет строку один в один.
- `hasAnyRole(...)`, `hasAnyAuthority(...)`.

Реальные правила строятся через `requestMatchers`:
- `GET /api/**` -> доступ всем ролям.
- `POST/PUT/DELETE` -> только ADMIN/MANAGER.

## 9. Method Security (аннотации на методах)
Включается `@EnableMethodSecurity`.
Примеры:
```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteCustomer(...) { }

@PreAuthorize("@security.canReadDeal(#dealId)")
public Deal getDeal(Long dealId) { }
```
Плюс: позволяет проверять доступ не только по URL, но и по бизнес-логике.

## 10. CSRF
CSRF защищает от подделки запросов, когда аутентификация через cookies.

Для JWT API обычно:
- `csrf().disable()` потому что токен идёт в заголовке, а не в cookie.

## 11. SessionManagement
- **STATEFUL**: сервер хранит сессию.
- **STATELESS**: каждый запрос несёт токен, сервер сессий не хранит.

Для JWT нужен `SessionCreationPolicy.STATELESS`.

## 12. JWT (ключевая часть)
JWT состоит из 3 частей:
```
header.payload.signature
```

Обязательные клеймы:
- `sub` (subject, обычно email)
- `exp` (expiration)
- `iat` (issued at)
- `iss` (issuer)

Типичная схема:
1) Пользователь логинится (`/auth/login`).
2) Возвращаем `accessToken`.
3) Клиент кладёт его в `Authorization: Bearer <token>`.
4) JWT фильтр валидирует токен и ставит Authentication в SecurityContext.

Важно помнить:
- Токен нельзя "отозвать" без дополнительной логики (blacklist).
- Для продакшена нужен refresh token или короткий expiration.
- Секрет нельзя хранить в репозитории.

## 13. CORS
Если фронт и бэкенд на разных доменах, нужен CORS:
- allowed origins
- allowed methods
- allowed headers (Authorization)

CORS - это не безопасность в плане auth, это браузерное правило.

## 14. Ошибки и ответы
- 401 Unauthorized -> нет или невалидный токен (AuthenticationEntryPoint).
- 403 Forbidden -> токен есть, но недостаточно прав (AccessDeniedHandler).

По умолчанию ответы не очень человекочитаемые, часто делают `@ControllerAdvice`.

## 15. Тестирование
Для unit/integration тестов:
- `@WithMockUser` создаёт пользователя без реального логина.
- Можно вручную положить `SecurityContext`.
- Для JWT фильтра чаще пишут интеграционные тесты.

## 16. Частые подводные камни
- забыли ROLE_ префикс -> `hasRole` не работает.
- включили CSRF и получили 403 на POST.
- забыли `@EnableMethodSecurity`, и `@PreAuthorize` не действует.
- хранят секрет JWT в коде.
- используют field injection для security компонентов.

## 17. Минимальный чек-лист JWT в проекте
1) `JwtProperties` (secret, expiration, issuer).
2) `JwtService` (создание/валидация токена).
3) `JwtAuthenticationFilter` (вытащить токен и положить в SecurityContext).
4) `UserDetailsService` + `PasswordEncoder`.
5) `SecurityFilterChain` (stateless, правила, фильтр).

## 18. Как объяснить на собесе (простая формулировка)
"В Spring Security запрос проходит через цепочку фильтров. Один из фильтров извлекает токен, валидирует, кладёт Authentication в SecurityContext, после чего срабатывают правила доступа. Если токен отсутствует - 401, если прав не хватает - 403. Для JWT мы делаем stateless и отключаем CSRF."

