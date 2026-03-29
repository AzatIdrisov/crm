# ============================================================
# Multi-stage build: уменьшает итоговый образ.
#
# Этап 1 (builder): компилируем и пакуем JAR с Maven.
# Этап 2 (runtime): только JRE + JAR, без Maven, src, .m2.
#
# Нюансы:
#  - eclipse-temurin: официальный OpenJDK от Adoptium, меньше уязвимостей чем openjdk.
#  - layertools extract: Spring Boot разбивает JAR на слои (dependencies, app).
#    Docker кэширует каждый слой отдельно — повторная сборка копирует только изменённый код,
#    а не все зависимости (они меняются реже).
#  - ENTRYPOINT ["java", ...]: exec form, не shell form.
#    Shell form запускает sh -c "java ...", что означает PID 1 = sh, а не java.
#    Exec form: PID 1 = java → SIGTERM доходит до JVM → graceful shutdown работает.
# ============================================================

# ─────────────────────────── Этап 1: сборка ───────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Копируем pom.xml отдельно — Docker кэширует слой с зависимостями.
# Если src/ изменился, Maven не будет заново скачивать библиотеки.
COPY pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || true

# Копируем исходники и собираем JAR (без тестов в Docker — тесты запускаем в CI).
COPY src ./src
RUN mvn package -DskipTests -q

# Разбиваем fat JAR на слои для эффективного кэширования Docker
RUN java -Djarmode=layertools -jar target/*.jar extract --destination /build/layers

# ─────────────────────────── Этап 2: runtime ───────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Не запускаем приложение от root — security best practice.
RUN addgroup -S crm && adduser -S crm -G crm
USER crm

WORKDIR /app

# Копируем слои в правильном порядке: сначала редко меняющиеся (зависимости),
# потом часто меняющиеся (код приложения).
# Docker инвалидирует кэш только для изменившихся слоёв.
COPY --from=builder /build/layers/dependencies/ ./
COPY --from=builder /build/layers/spring-boot-loader/ ./
COPY --from=builder /build/layers/snapshot-dependencies/ ./
COPY --from=builder /build/layers/application/ ./

EXPOSE 8080

# JVM настройки для контейнера:
#  -XX:+UseContainerSupport    — JVM читает cgroup лимиты (не хост RAM).
#  -XX:MaxRAMPercentage=75.0   — использовать до 75% выделенной памяти.
#  -XX:+ExitOnOutOfMemoryError — убить процесс при OOM, Docker перезапустит.
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "org.springframework.boot.loader.launch.JarLauncher"]
