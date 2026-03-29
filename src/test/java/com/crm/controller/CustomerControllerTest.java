package com.crm.controller;

import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.model.Customer;
import com.crm.model.value.Email;
import com.crm.model.value.PhoneNumber;
import com.crm.service.CustomerService;
import com.crm.validation.UniqueEmailValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest для CustomerController.
 *
 * Ключевые концепции:
 *  - @WebMvcTest: поднимает только веб-слой. Загружает: контроллеры, @ControllerAdvice,
 *    фильтры, конвертеры, WebMvcConfigurer. НЕ загружает: @Service, @Repository,
 *    полный Spring контекст, БД, Redis.
 *
 *  - excludeAutoConfiguration: отключаем Spring Security авто-конфигурацию.
 *    SecurityConfig требует JwtAuthenticationFilter → JwtService → JwtProperties...
 *    Для тестирования HTTP-логики контроллера безопасность нас не интересует.
 *    SecurityAutoConfiguration — основная конфигурация (UserDetails, AuthProvider).
 *    SecurityFilterAutoConfiguration — настройка DelegatingFilterProxy.
 *    Нужно отключить оба, иначе фильтр цепочка всё равно применится.
 *
 *  - @MockBean: создаёт Mockito-mock и регистрирует его как Spring-бин в контексте.
 *    Отличие от @Mock: бин доступен для @Autowired и для injection в другие бины.
 *    Здесь CustomerService — @MockBean, потому что UniqueEmailValidator инжектирует
 *    его через конструктор (Spring-managed validator).
 *
 *  - @Import(UniqueEmailValidator.class): @WebMvcTest НЕ сканирует @Component бины.
 *    UniqueEmailValidator — @Component с зависимостью от CustomerService.
 *    Явно добавляем в контекст, чтобы @UniqueEmail аннотация работала при валидации.
 *
 *  - MockMvc: эмулирует HTTP-запросы без реального сервера (Tomcat не запускается).
 *    Запрос проходит через всю цепочку Spring MVC: DispatcherServlet → Handler →
 *    MessageConverter → @ControllerAdvice.
 *
 *  - GlobalExceptionHandler (@RestControllerAdvice) подхватывается @WebMvcTest
 *    автоматически — не нужно явно импортировать.
 */
@WebMvcTest(
        controllers = CustomerController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(UniqueEmailValidator.class)
@DisplayName("CustomerController @WebMvcTest")
class CustomerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // @MockBean: mock регистрируется в Spring контексте.
    // UniqueEmailValidator получит этот же mock через конструктор.
    @MockBean
    CustomerService customerService;

    private Customer alice;
    private CustomerResponse aliceResponse;

    @BeforeEach
    void setUp() {
        alice = Customer.builder()
                .id(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email(new Email("alice@test.com"))
                .phone(new PhoneNumber("+7 999 000-00-00"))
                .company("Acme Corp")
                .build();

        aliceResponse = new CustomerResponse();
        aliceResponse.setId(1L);
        aliceResponse.setFirstName("Alice");
        aliceResponse.setLastName("Smith");
        aliceResponse.setEmail("alice@test.com");
        aliceResponse.setPhone("+7 999 000-00-00");
        aliceResponse.setCompany("Acme Corp");
    }

    // =========================================================================
    // GET /api/customers
    // =========================================================================

    @Test
    @DisplayName("GET /api/customers: 200 OK + список клиентов")
    void findAll_returnsOkWithList() throws Exception {
        when(customerService.findAll()).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                // jsonPath: проверяем JSON-структуру ответа
                // $[0] — первый элемент массива
                .andExpect(jsonPath("$[0].firstName").value("Alice"))
                .andExpect(jsonPath("$[0].lastName").value("Smith"))
                .andExpect(jsonPath("$[0].email").value("alice@test.com"));
    }

    @Test
    @DisplayName("GET /api/customers: 200 OK + пустой список")
    void findAll_returnsOkWithEmptyList() throws Exception {
        when(customerService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // =========================================================================
    // GET /api/customers/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /api/customers/1: 200 OK если клиент найден")
    void findById_whenFound_returnsOk() throws Exception {
        when(customerService.findById(1L)).thenReturn(Optional.of(alice));

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.company").value("Acme Corp"));
    }

    @Test
    @DisplayName("GET /api/customers/99: 404 если клиент не найден")
    void findById_whenNotFound_returns404() throws Exception {
        when(customerService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // POST /api/customers
    // =========================================================================

    @Test
    @DisplayName("POST /api/customers: 201 Created при валидном запросе")
    void create_withValidRequest_returns201() throws Exception {
        CustomerRequest request = validRequest("alice@test.com");

        // UniqueEmailValidator вызовет customerService.findByEmail() → мок вернёт empty → email уникален
        when(customerService.findByEmail(any())).thenReturn(Optional.empty());
        when(customerService.save(any(Customer.class))).thenReturn(alice);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@test.com"));
    }

    @Test
    @DisplayName("POST /api/customers: 400 если firstName пустой (@NotBlank)")
    void create_withBlankFirstName_returns400() throws Exception {
        CustomerRequest request = validRequest("alice@test.com");
        request.setFirstName("");  // нарушаем @NotBlank

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                // GlobalExceptionHandler возвращает поле "status" в ErrorResponse
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/customers: 400 если email невалидный (@Email)")
    void create_withInvalidEmail_returns400() throws Exception {
        CustomerRequest request = validRequest("not-an-email");  // нарушаем @Email

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PUT /api/customers/{id}
    // =========================================================================

    @Test
    @DisplayName("PUT /api/customers/1: 200 OK если клиент найден")
    void update_whenFound_returns200() throws Exception {
        CustomerRequest request = validRequest("alice@test.com");

        when(customerService.findByEmail(any())).thenReturn(Optional.empty());
        when(customerService.findById(1L)).thenReturn(Optional.of(alice));
        when(customerService.save(any(Customer.class))).thenReturn(alice);

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/customers/99: 404 если клиент не найден")
    void update_whenNotFound_returns404() throws Exception {
        CustomerRequest request = validRequest("alice@test.com");
        when(customerService.findByEmail(any())).thenReturn(Optional.empty());
        when(customerService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/customers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/customers/{id}
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/customers/1: 204 No Content если клиент удалён")
    void delete_whenFound_returns204() throws Exception {
        when(customerService.deleteById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/customers/99: 404 если клиент не найден")
    void delete_whenNotFound_returns404() throws Exception {
        when(customerService.deleteById(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/customers/99"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Вспомогательные методы
    // =========================================================================

    /**
     * Создаёт валидный CustomerRequest для использования в тестах.
     * Вынесено в отдельный метод чтобы не дублировать создание в каждом тесте.
     */
    private CustomerRequest validRequest(String email) {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName("Alice");
        request.setLastName("Smith");
        request.setEmail(email);
        request.setPhone("+7 999 000-00-00");
        request.setCompany("Acme Corp");
        return request;
    }
}
