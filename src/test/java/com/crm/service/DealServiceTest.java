package com.crm.service;

import com.crm.event.DealStatusChangedEvent;
import com.crm.exception.ResourceNotFoundException;
import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.model.value.Money;
import com.crm.repository.DealRepository;
import com.crm.repository.spec.DealSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты DealService.
 *
 * Ключевые концепции:
 *  - ArgumentCaptor: перехватывает аргумент, переданный в mock, для последующей
 *    проверки его полей. Используется когда аргумент создаётся внутри тестируемого
 *    метода (например, new DealStatusChangedEvent(...)).
 *
 *  - any(Class): Mockito-матчер — принимает любой аргумент указанного типа.
 *    Используется когда нас не интересует конкретное значение аргумента.
 *
 *  - @SuppressWarnings("unchecked"): нужен для captor.capture() с generic-типами
 *    (Specification<Deal>) чтобы подавить предупреждение компилятора.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DealService Unit Tests")
class DealServiceTest {

    @Mock
    DealRepository dealRepository;

    @Mock
    ApplicationEventPublisher publisher;

    @InjectMocks
    DealService dealService;

    private Deal deal;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id(10L)
                .firstName("Alice")
                .lastName("Smith")
                .build();

        deal = Deal.builder()
                .id(1L)
                .title("Big Deal")
                .status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("100000")))
                .customer(customer)
                .build();
    }

    // =========================================================================
    // findByIdWithDetails
    // =========================================================================

    @Test
    @DisplayName("findByIdWithDetails: возвращает сделку если найдена")
    void findByIdWithDetails_whenFound_returnsOptionalDeal() {
        when(dealRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(deal));

        Optional<Deal> result = dealService.findByIdWithDetails(1L);

        assertThat(result).isPresent().contains(deal);
        verify(dealRepository).findByIdWithDetails(1L);
    }

    @Test
    @DisplayName("findByIdWithDetails: возвращает Optional.empty() если не найдена")
    void findByIdWithDetails_whenNotFound_returnsEmpty() {
        when(dealRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        Optional<Deal> result = dealService.findByIdWithDetails(99L);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // save
    // =========================================================================

    @Test
    @DisplayName("save: делегирует в репозиторий и возвращает сохранённую сделку")
    void save_delegatesToRepository() {
        when(dealRepository.save(any(Deal.class))).thenReturn(deal);

        Deal result = dealService.save(deal);

        assertThat(result).isEqualTo(deal);
        verify(dealRepository).save(deal);
    }

    // =========================================================================
    // deleteById
    // =========================================================================

    @Test
    @DisplayName("deleteById: возвращает false если сделка не существует")
    void deleteById_whenNotExists_returnsFalse() {
        when(dealRepository.existsById(99L)).thenReturn(false);

        boolean result = dealService.deleteById(99L);

        assertThat(result).isFalse();
        verify(dealRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteById: удаляет сделку и возвращает true")
    void deleteById_whenExists_deletesAndReturnsTrue() {
        when(dealRepository.existsById(1L)).thenReturn(true);

        boolean result = dealService.deleteById(1L);

        assertThat(result).isTrue();
        verify(dealRepository).deleteById(1L);
    }

    // =========================================================================
    // changeStatus
    // =========================================================================

    @Test
    @DisplayName("changeStatus: бросает ResourceNotFoundException если сделка не найдена")
    void changeStatus_whenNotFound_throwsResourceNotFoundException() {
        when(dealRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.changeStatus(99L, DealStatus.WON))
                .isInstanceOf(ResourceNotFoundException.class);

        // Событие НЕ должно публиковаться если сделка не найдена
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("changeStatus: обновляет статус и публикует DealStatusChangedEvent")
    void changeStatus_updatesStatusAndPublishesEvent() {
        when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

        Deal result = dealService.changeStatus(1L, DealStatus.WON);

        // Статус обновился на объекте
        assertThat(result.getStatus()).isEqualTo(DealStatus.WON);

        // ArgumentCaptor захватывает событие, переданное в publisher
        ArgumentCaptor<DealStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(DealStatusChangedEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());

        DealStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.getDealId()).isEqualTo(1L);
        assertThat(event.getOldStatus()).isEqualTo(DealStatus.NEW);  // было
        assertThat(event.getNewStatus()).isEqualTo(DealStatus.WON);  // стало
    }

    @Test
    @DisplayName("changeStatus: возвращает сделку с новым статусом")
    void changeStatus_returnsDealWithNewStatus() {
        when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

        Deal result = dealService.changeStatus(1L, DealStatus.LOST);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(DealStatus.LOST);
    }

    // =========================================================================
    // bulkUpdateStatus
    // =========================================================================

    @Test
    @DisplayName("bulkUpdateStatus: делегирует в репозиторий и возвращает количество строк")
    void bulkUpdateStatus_returnsUpdatedCount() {
        when(dealRepository.updateStatus(1L, DealStatus.WON)).thenReturn(1);

        int count = dealService.bulkUpdateStatus(1L, DealStatus.WON);

        assertThat(count).isEqualTo(1);
        verify(dealRepository).updateStatus(1L, DealStatus.WON);
    }

    // =========================================================================
    // search — Specification
    // =========================================================================

    @Test
    @DisplayName("search: вызывает findAll со сформированной Specification")
    @SuppressWarnings("unchecked")
    void search_callsRepositoryWithSpec() {
        when(dealRepository.findAll(any(Specification.class))).thenReturn(List.of(deal));

        List<Deal> result = dealService.search(DealStatus.NEW, 10L, null, null);

        assertThat(result).containsExactly(deal);
        // Проверяем что findAll(Specification) был вызван (а не findAll())
        verify(dealRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("search: при всех null параметрах всё равно вызывает findAll(spec)")
    @SuppressWarnings("unchecked")
    void search_withNullParams_callsRepositoryWithSpec() {
        when(dealRepository.findAll(any(Specification.class))).thenReturn(List.of());

        List<Deal> result = dealService.search(null, null, null, null);

        assertThat(result).isEmpty();
        verify(dealRepository).findAll(any(Specification.class));
    }
}
