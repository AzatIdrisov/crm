package com.crm.config;

/**
 * Константы имён кэшей — используются в @Cacheable / @CacheEvict / @CachePut.
 *
 * Зачем: избегаем magic strings вида @Cacheable("customeers") — опечатку
 * компилятор не поймает, а кэш просто перестанет работать молча.
 */
public final class CacheNames {

    public static final String CUSTOMERS = "customers";
    public static final String DEALS     = "deals";
    public static final String USERS     = "users";

    private CacheNames() {}
}
