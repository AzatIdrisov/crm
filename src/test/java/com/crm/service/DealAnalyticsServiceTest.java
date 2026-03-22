package com.crm.service;

import com.crm.model.Deal;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DealAnalyticsServiceTest {

    private DealAnalyticsService service;

    private User user1;
    private User user2;
    private Deal dealNewSmall;    // NEW,  1000 RUB, user1, id=1
    private Deal dealNewLarge;    // NEW,  3000 RUB, user2, id=3
    private Deal dealWon;         // WON,  5000 RUB, user1, id=2
    private Deal dealWon2;        // WON,  2000 RUB, user2, id=5
    private Deal dealLostNoData;  // LOST, amount=null, assignedTo=null, id=4

    @BeforeEach
    void setUp() {
        service = new DealAnalyticsService();

        user1 = User.builder().id(1L).firstName("Alice").lastName("Smith").role(UserRole.MANAGER).build();
        user2 = User.builder().id(2L).firstName("Bob").lastName("Jones").role(UserRole.MANAGER).build();

        dealNewSmall = Deal.builder().id(1L).title("Deal A").status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("1000"))).assignedTo(user1).build();

        dealWon = Deal.builder().id(2L).title("Deal B").status(DealStatus.WON)
                .amount(Money.ofRub(new BigDecimal("5000"))).assignedTo(user1).build();

        dealNewLarge = Deal.builder().id(3L).title("Deal C").status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("3000"))).assignedTo(user2).build();

        dealLostNoData = Deal.builder().id(4L).title("Deal D").status(DealStatus.LOST)
                .amount(null).assignedTo(null).build();

        dealWon2 = Deal.builder().id(5L).title("Deal E").status(DealStatus.WON)
                .amount(Money.ofRub(new BigDecimal("2000"))).assignedTo(user2).build();
    }

    // --- 2.1.1 filterByStatus ---

    @Test
    void filterByStatus_shouldReturnOnlyMatchingDeals() {
        List<Deal> result = service.filterByStatus(allDeals(), DealStatus.NEW);
        assertThat(result).containsExactlyInAnyOrder(dealNewSmall, dealNewLarge);
    }

    @Test
    void filterByStatus_shouldReturnEmptyWhenNoneMatch() {
        List<Deal> result = service.filterByStatus(allDeals(), DealStatus.ON_HOLD);
        assertThat(result).isEmpty();
    }

    // --- 2.1.2 getTitles ---

    @Test
    void getTitles_shouldReturnAllTitles() {
        List<String> titles = service.getTitles(List.of(dealNewSmall, dealWon));
        assertThat(titles).containsExactly("Deal A", "Deal B");
    }

    @Test
    void getTitles_shouldReturnEmptyForEmptyList() {
        assertThat(service.getTitles(List.of())).isEmpty();
    }

    // --- 2.1.3 groupByStatus ---

    @Test
    void groupByStatus_shouldGroupCorrectly() {
        Map<DealStatus, List<Deal>> result = service.groupByStatus(allDeals());
        assertThat(result.get(DealStatus.NEW)).containsExactlyInAnyOrder(dealNewSmall, dealNewLarge);
        assertThat(result.get(DealStatus.WON)).containsExactlyInAnyOrder(dealWon, dealWon2);
        assertThat(result.get(DealStatus.LOST)).containsExactly(dealLostNoData);
    }

    // --- 2.1.4 countByStatus ---

    @Test
    void countByStatus_shouldCountCorrectly() {
        Map<DealStatus, Long> result = service.countByStatus(allDeals());
        assertThat(result.get(DealStatus.NEW)).isEqualTo(2L);
        assertThat(result.get(DealStatus.WON)).isEqualTo(2L);
        assertThat(result.get(DealStatus.LOST)).isEqualTo(1L);
    }

    // --- 2.1.5 groupByManager ---

    @Test
    void groupByManager_shouldSkipDealsWithoutManager() {
        Map<User, List<Deal>> result = service.groupByManager(allDeals());
        assertThat(result).doesNotContainKey(null);
        assertThat(result.get(user1)).containsExactlyInAnyOrder(dealNewSmall, dealWon);
        assertThat(result.get(user2)).containsExactlyInAnyOrder(dealNewLarge, dealWon2);
    }

    // --- 2.1.6 totalAmount ---

    @Test
    void totalAmount_shouldSumAllAmounts() {
        Optional<Money> result = service.totalAmount(allDeals());
        // 1000 + 5000 + 3000 + 2000 = 11000 (dealLostNoData пропускается)
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("11000.00"));
    }

    @Test
    void totalAmount_shouldReturnEmptyForEmptyList() {
        assertThat(service.totalAmount(List.of())).isEmpty();
    }

    // --- 2.1.7 totalAmountByStatus ---

    @Test
    void totalAmountByStatus_shouldSumPerStatus() {
        Map<DealStatus, Double> result = service.totalAmountByStatus(allDeals());
        assertThat(result.get(DealStatus.NEW)).isEqualTo(4000.0);   // 1000 + 3000
        assertThat(result.get(DealStatus.WON)).isEqualTo(7000.0);   // 5000 + 2000
        assertThat(result).doesNotContainKey(DealStatus.LOST);       // null amount — пропущен
    }

    // --- 2.1.8 getTopDeals ---

    @Test
    void getTopDeals_shouldReturnTopNByAmountDescending() {
        List<Deal> top2 = service.getTopDeals(allDeals(), 2);
        assertThat(top2).containsExactly(dealWon, dealNewLarge); // 5000, 3000
    }

    @Test
    void getTopDeals_shouldSkipDealsWithoutAmount() {
        List<Deal> top10 = service.getTopDeals(allDeals(), 10);
        assertThat(top10).doesNotContain(dealLostNoData);
    }

    // --- 2.1.9 findLargest ---

    @Test
    void findLargest_shouldReturnDealWithMaxAmount() {
        Optional<Deal> result = service.findLargest(allDeals());
        assertThat(result).contains(dealWon); // 5000 RUB
    }

    @Test
    void findLargest_shouldReturnEmptyForEmptyList() {
        assertThat(service.findLargest(List.of())).isEmpty();
    }

    // --- 2.1.10 hasWonDeals ---

    @Test
    void hasWonDeals_shouldReturnTrueWhenWonExists() {
        assertThat(service.hasWonDeals(allDeals())).isTrue();
    }

    @Test
    void hasWonDeals_shouldReturnFalseWhenNoWon() {
        assertThat(service.hasWonDeals(List.of(dealNewSmall, dealLostNoData))).isFalse();
    }

    // --- 2.1.11 allDealsAssigned ---

    @Test
    void allDealsAssigned_shouldReturnFalseWhenSomeUnassigned() {
        assertThat(service.allDealsAssigned(allDeals())).isFalse();
    }

    @Test
    void allDealsAssigned_shouldReturnTrueWhenAllAssigned() {
        assertThat(service.allDealsAssigned(List.of(dealNewSmall, dealWon))).isTrue();
    }

    // --- 2.1.12 noDealsWithoutAmount ---

    @Test
    void noDealsWithoutAmount_shouldReturnFalseWhenSomeMissing() {
        assertThat(service.noDealsWithoutAmount(allDeals())).isFalse();
    }

    @Test
    void noDealsWithoutAmount_shouldReturnTrueWhenAllHaveAmount() {
        assertThat(service.noDealsWithoutAmount(List.of(dealNewSmall, dealWon))).isTrue();
    }

    // --- 2.1.13 summaryReport ---

    @Test
    void summaryReport_shouldFormatEachDealAndJoinWithNewline() {
        String report = service.summaryReport(List.of(dealNewSmall, dealWon));
        assertThat(report).isEqualTo(
                "Deal A [NEW] — 1000.00 RUB\n" +
                "Deal B [WON] — 5000.00 RUB"
        );
    }

    // --- 2.1.14 getDistinctManagers ---

    @Test
    void getDistinctManagers_shouldReturnUniqueManagers() {
        List<List<Deal>> groups = List.of(
                List.of(dealNewSmall, dealWon),    // user1, user1
                List.of(dealNewLarge, dealWon2)    // user2, user2
        );
        List<User> managers = service.getDistinctManagers(groups);
        assertThat(managers).containsExactlyInAnyOrder(user1, user2);
    }

    @Test
    void getDistinctManagers_shouldSkipNullManagers() {
        List<List<Deal>> groups = List.of(List.of(dealLostNoData));
        assertThat(service.getDistinctManagers(groups)).isEmpty();
    }

    // --- 2.1.15 indexById ---

    @Test
    void indexById_shouldMapIdToDeal() {
        Map<Long, Deal> index = service.indexById(List.of(dealNewSmall, dealWon));
        assertThat(index.get(1L)).isEqualTo(dealNewSmall);
        assertThat(index.get(2L)).isEqualTo(dealWon);
    }

    @Test
    void indexById_shouldSkipDealsWithoutId() {
        Deal noId = Deal.builder().title("No ID").status(DealStatus.NEW).build();
        Map<Long, Deal> index = service.indexById(List.of(dealNewSmall, noId));
        assertThat(index).hasSize(1);
    }

    // --- 2.1.16 partitionByWon ---

    @Test
    void partitionByWon_shouldSplitCorrectly() {
        Map<Boolean, List<Deal>> result = service.partitionByWon(allDeals());
        assertThat(result.get(true)).containsExactlyInAnyOrder(dealWon, dealWon2);
        assertThat(result.get(false)).containsExactlyInAnyOrder(dealNewSmall, dealNewLarge, dealLostNoData);
    }

    // --- вспомогательный метод ---

    private List<Deal> allDeals() {
        return List.of(dealNewSmall, dealWon, dealNewLarge, dealLostNoData, dealWon2);
    }
}
