package com.crm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Конфигурация Redis-кэша.
 *
 * Ключевые концепции:
 *  - RedisCacheConfiguration: настройки одного кэша (TTL, сериализатор, null-значения).
 *
 *  - TTL (Time To Live): через какое время запись автоматически удаляется из Redis.
 *    Слишком долгий TTL → устаревшие данные. Слишком короткий → много промахов кэша.
 *
 *  - GenericJackson2JsonRedisSerializer: сериализует объекты в JSON + добавляет
 *    поле "@class" для десериализации без явного указания типа.
 *    Пример: {"@class":"com.crm.model.Customer","id":1,"firstName":"John",...}
 *
 *  - StringRedisSerializer для ключей: ключи хранятся как читаемые строки
 *    вида "customers::1", а не бинарные данные.
 *
 *  - disableCachingNullValues(): не кэшируем null — иначе "не найдено" закэшируется
 *    и при следующем запросе мы получим null из кэша, а не свежий результат из БД.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {

        // Сериализатор значений — JSON с информацией о типе
        var jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Базовая конфигурация — применяется ко всем кэшам если не переопределена
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // Кастомный TTL на каждый кэш — разные данные меняются с разной частотой
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                CacheNames.USERS,     defaults.entryTtl(Duration.ofMinutes(30)), // пользователи меняются редко
                CacheNames.CUSTOMERS, defaults.entryTtl(Duration.ofMinutes(10)),
                CacheNames.DEALS,     defaults.entryTtl(Duration.ofMinutes(5))   // сделки меняются чаще
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
