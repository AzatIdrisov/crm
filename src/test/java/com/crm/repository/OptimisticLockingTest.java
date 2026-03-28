package com.crm.repository;

import com.crm.model.Deal;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Демонстрация оптимистичной блокировки через @Version.
 *
 * Проблема без @Version (Lost Update):
 *   Thread 1: READ deal(id=1, version=0) → status=NEW
 *   Thread 2: READ deal(id=1, version=0) → status=NEW
 *   Thread 1: UPDATE deal SET status=WON WHERE id=1   ← успешно, version не проверяется
 *   Thread 2: UPDATE deal SET status=LOST WHERE id=1  ← перезаписывает изменения Thread 1!
 *   Результат: WON потеряно, в БД LOST — "потеря обновления" (Lost Update)
 *
 * С @Version:
 *   Thread 1: UPDATE deals SET status=WON, version=1 WHERE id=1 AND version=0  → 1 строка
 *   Thread 2: UPDATE deals SET status=LOST, version=1 WHERE id=1 AND version=0 → 0 строк!
 *   Hibernate: 0 строк обновлено → OptimisticLockException → Spring: OptimisticLockingFailureException
 */
class OptimisticLockingTest extends AbstractRepositoryTest {

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // -------------------------------------------------------------------------
    // Тест 1: @Version инкрементируется при каждом UPDATE
    // -------------------------------------------------------------------------

    @Test
    void version_startsAtNull_incrementsOnEachUpdate() {
        User user = userRepository.save(buildUser("version@example.com", UserRole.MANAGER));
        Deal deal = buildDeal("Version Test", DealStatus.NEW, null, user);

        // Новый объект: version == null (ещё не сохранён)
        assertThat(deal.getVersion()).isNull();

        Deal saved = dealRepository.saveAndFlush(deal);

        // После первого INSERT: version = 0 (Hibernate устанавливает DEFAULT 0)
        assertThat(saved.getVersion()).isEqualTo(0L);

        // После первого UPDATE: version = 1
        saved.setStatus(DealStatus.IN_PROGRESS);
        Deal updated = dealRepository.saveAndFlush(saved);
        assertThat(updated.getVersion()).isEqualTo(1L);

        // После второго UPDATE: version = 2
        updated.setStatus(DealStatus.WON);
        Deal updated2 = dealRepository.saveAndFlush(updated);
        assertThat(updated2.getVersion()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // Тест 2: Конкурентное обновление в ОДНОЙ транзакции через detach
    // -------------------------------------------------------------------------

    @Test
    void optimisticLock_staleVersion_throwsException() {
        // Сохраняем сделку
        User user = userRepository.save(buildUser("stale@example.com", UserRole.MANAGER));
        Deal deal = dealRepository.saveAndFlush(buildDeal("Stale Deal", DealStatus.NEW, null, user));
        assertThat(deal.getVersion()).isEqualTo(0L);

        // Загружаем ПЕРВЫЙ экземпляр объекта (version = 0)
        Deal deal1 = dealRepository.findById(deal.getId()).orElseThrow();

        // Очищаем L1 кэш — следующий findById пойдёт в БД и вернёт новый объект
        entityManager.clear();

        // Загружаем ВТОРОЙ экземпляр (version = 0) — симулируем "другой запрос"
        Deal deal2 = dealRepository.findById(deal.getId()).orElseThrow();

        // "Первый пользователь" обновляет сделку → version в БД становится 1
        deal1.setStatus(DealStatus.WON);
        dealRepository.saveAndFlush(deal1); // UPDATE ... WHERE id=? AND version=0 → OK, version=1

        // "Второй пользователь" пытается обновить ту же сделку с устаревшей version=0
        deal2.setStatus(DealStatus.LOST);

        // Hibernate генерирует: UPDATE deals SET status=LOST, version=1 WHERE id=? AND version=0
        // В БД version уже 1 → WHERE version=0 не найдёт строку → 0 rows affected
        // → Hibernate: StaleStateException → Spring: OptimisticLockingFailureException
        assertThatThrownBy(() -> dealRepository.saveAndFlush(deal2))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Убеждаемся, что "победило" изменение первого пользователя
        entityManager.clear();
        Deal final_ = dealRepository.findById(deal.getId()).orElseThrow();
        assertThat(final_.getStatus()).isEqualTo(DealStatus.WON);
        assertThat(final_.getVersion()).isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Тест 3: Конкурентное обновление в разных потоках / транзакциях
    // -------------------------------------------------------------------------

    @Test
    void optimisticLock_concurrentThreads_oneWinsOneThrows() throws InterruptedException {
        // Создаём и сохраняем deal в отдельной транзакции (committed в БД)
        Long dealId = transactionTemplate.execute(status -> {
            User user = userRepository.save(buildUser("threads@example.com", UserRole.MANAGER));
            Deal d = buildDeal("Concurrent Deal", DealStatus.NEW, null, user);
            return dealRepository.save(d).getId();
        });

        // Лэтч: оба потока загружают deal одновременно (version=0 у обоих)
        CountDownLatch bothLoaded = new CountDownLatch(2);
        // Лэтч: первый поток не коммитит, пока второй не загрузил deal
        CountDownLatch firstCanCommit = new CountDownLatch(1);

        AtomicReference<Exception> thread2Error = new AtomicReference<>();

        // Поток 1: загружает deal, ждёт разрешения, меняет на WON
        Thread thread1 = new Thread(() -> transactionTemplate.execute(status -> {
            Deal d = dealRepository.findById(dealId).orElseThrow();
            bothLoaded.countDown();
            try { firstCanCommit.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            d.setStatus(DealStatus.WON);
            dealRepository.saveAndFlush(d); // version: 0 → 1
            return null;
        }));

        // Поток 2: загружает deal (тоже version=0), сразу пытается изменить на LOST
        Thread thread2 = new Thread(() -> {
            try {
                transactionTemplate.execute(status -> {
                    Deal d = dealRepository.findById(dealId).orElseThrow();
                    bothLoaded.countDown();
                    // Ждём, пока Thread 1 закоммитит изменение
                    try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    d.setStatus(DealStatus.LOST);
                    dealRepository.saveAndFlush(d); // version=0, но в БД уже 1 → FAIL
                    return null;
                });
            } catch (Exception e) {
                thread2Error.set(e);
            }
        });

        thread1.start();
        thread2.start();

        // Ждём пока оба потока загрузят deal
        bothLoaded.await();
        // Разрешаем Thread 1 коммитить первым
        firstCanCommit.countDown();

        thread1.join(5000);
        thread2.join(5000);

        // Thread 2 должен получить исключение оптимистичной блокировки
        assertThat(thread2Error.get())
                .isNotNull()
                .isInstanceOf(OptimisticLockingFailureException.class);

        // В БД — результат Thread 1 (WON)
        transactionTemplate.execute(status -> {
            entityManager.clear();
            Deal final_ = dealRepository.findById(dealId).orElseThrow();
            assertThat(final_.getStatus()).isEqualTo(DealStatus.WON);
            assertThat(final_.getVersion()).isEqualTo(1L);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Тест 4: Dirty checking — save() не нужен при @Transactional
    // -------------------------------------------------------------------------

    @Test
    void dirtyChecking_noExplicitSave_updatesOnCommit() {
        // Нюанс: внутри @Transactional можно НЕ вызывать save().
        // Hibernate dirty-checking при коммите транзакции находит изменённые поля
        // и генерирует UPDATE автоматически. Это называется "implicit persistence".

        User user = userRepository.save(buildUser("dirty@example.com", UserRole.MANAGER));
        Deal deal = dealRepository.saveAndFlush(buildDeal("Dirty Check", DealStatus.NEW, null, user));
        Long id = deal.getId();
        long versionBefore = deal.getVersion();

        // Загружаем и изменяем без явного save()
        Deal loaded = dealRepository.findById(id).orElseThrow();
        loaded.setStatus(DealStatus.IN_PROGRESS); // не вызываем save()!

        // Flush: Hibernate находит изменение, генерирует UPDATE
        entityManager.flush();
        entityManager.clear();

        Deal result = dealRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(DealStatus.IN_PROGRESS);
        // Version инкрементировалась, значит UPDATE был выполнен
        assertThat(result.getVersion()).isEqualTo(versionBefore + 1);
    }
}
