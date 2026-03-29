package com.crm.controller;

import com.crm.dto.deal.DealRequest;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.model.value.Money;
import com.crm.service.DealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest для DealController.
 *
 * Ключевые концепции:
 *  - DealRequest НЕ имеет custom Spring-validators (только Bean Validation аннотации),
 *    поэтому @Import не нужен — стандартные валидаторы Hibernate Validator работают
 *    автоматически.
 *
 *  - MockMvc.perform() строит HTTP-запрос:
 *    .contentType(APPLICATION_JSON) — заголовок Content-Type
 *    .content(json)                 — тело запроса
 *
 *  - jsonPath("$.field")        — проверяем поле корневого объекта
 *    jsonPath("$[0].field")     — проверяем поле первого элемента массива
 *    jsonPath("$.status").value(201) — проверяем значение
 */
@WebMvcTest(
        controllers = DealController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@TestPropertySource(properties = "spring.cache.type=none")
@DisplayName("DealController @WebMvcTest")
class DealControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    DealService dealService;

    // JwtAuthenticationFilter (@Component) подхватывается @WebMvcTest и требует эти бины.
    @MockBean
    com.crm.security.JwtService jwtService;
    @MockBean
    UserDetailsService userDetailsService;

    private Deal deal;

    @BeforeEach
    void setUp() {
        deal = Deal.builder()
                .id(1L)
                .title("Big Deal")
                .status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("50000")))
                .build();
    }

    // =========================================================================
    // GET /api/deals
    // =========================================================================

    @Test
    @DisplayName("GET /api/deals: 200 OK + список сделок")
    void findAll_returnsOkWithList() throws Exception {
        when(dealService.findAll()).thenReturn(List.of(deal));

        mockMvc.perform(get("/api/deals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Big Deal"))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    @DisplayName("GET /api/deals: 200 OK + пустой список")
    void findAll_returnsOkWithEmptyList() throws Exception {
        when(dealService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/deals"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // =========================================================================
    // GET /api/deals/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /api/deals/1: 200 OK если сделка найдена")
    void findById_whenFound_returnsOk() throws Exception {
        when(dealService.findById(1L)).thenReturn(Optional.of(deal));

        mockMvc.perform(get("/api/deals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Big Deal"))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    @DisplayName("GET /api/deals/99: 404 если сделка не найдена")
    void findById_whenNotFound_returns404() throws Exception {
        when(dealService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/deals/99"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // POST /api/deals
    // =========================================================================

    @Test
    @DisplayName("POST /api/deals: 201 Created при валидном запросе")
    void create_withValidRequest_returns201() throws Exception {
        DealRequest request = validRequest();
        when(dealService.save(any(Deal.class))).thenReturn(deal);

        mockMvc.perform(post("/api/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Big Deal"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @DisplayName("POST /api/deals: 400 если title пустой (@NotBlank)")
    void create_withBlankTitle_returns400() throws Exception {
        DealRequest request = validRequest();
        request.setTitle("");  // нарушаем @NotBlank

        mockMvc.perform(post("/api/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/deals: 400 если status = null (@NotNull)")
    void create_withNullStatus_returns400() throws Exception {
        DealRequest request = validRequest();
        request.setStatus(null);  // нарушаем @NotNull

        mockMvc.perform(post("/api/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/deals: 400 если amount отрицательный (@DecimalMin)")
    void create_withNegativeAmount_returns400() throws Exception {
        DealRequest request = validRequest();
        request.setAmount(new BigDecimal("-100"));  // нарушаем @DecimalMin(0)

        mockMvc.perform(post("/api/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PUT /api/deals/{id}
    // =========================================================================

    @Test
    @DisplayName("PUT /api/deals/1: 200 OK если сделка найдена и обновлена")
    void update_whenFound_returns200() throws Exception {
        DealRequest request = validRequest();
        when(dealService.findById(1L)).thenReturn(Optional.of(deal));
        when(dealService.save(any(Deal.class))).thenReturn(deal);

        mockMvc.perform(put("/api/deals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/deals/99: 404 если сделка не найдена")
    void update_whenNotFound_returns404() throws Exception {
        DealRequest request = validRequest();
        when(dealService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/deals/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/deals/{id}
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/deals/1: 204 No Content если сделка удалена")
    void delete_whenFound_returns204() throws Exception {
        when(dealService.deleteById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/deals/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/deals/99: 404 если сделка не найдена")
    void delete_whenNotFound_returns404() throws Exception {
        when(dealService.deleteById(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/deals/99"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Вспомогательные методы
    // =========================================================================

    private DealRequest validRequest() {
        DealRequest request = new DealRequest();
        request.setTitle("Big Deal");
        request.setAmount(new BigDecimal("50000"));
        request.setCurrency("RUB");
        request.setStatus(DealStatus.NEW);
        return request;
    }
}
