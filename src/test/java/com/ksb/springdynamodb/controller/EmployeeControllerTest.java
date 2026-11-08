package com.ksb.springdynamodb.controller;

import com.ksb.springdynamodb.dto.EmployeeRequest;
import com.ksb.springdynamodb.exception.EmployeeNotFoundException;
import com.ksb.springdynamodb.model.Employee;
import com.ksb.springdynamodb.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void createReturnsCreatedEmployee() throws Exception {
        when(employeeService.create(any(EmployeeRequest.class))).thenReturn(employee("emp-1"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/employees/emp-1"))
                .andExpect(jsonPath("$.id").value("emp-1"))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void createRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Ada",
                                  "lastName": "Lovelace",
                                  "email": "not-an-email",
                                  "department": "Engineering",
                                  "title": "Engineer",
                                  "salary": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").value("Email must be valid"));
    }

    @Test
    void findAllReturnsEmployees() throws Exception {
        when(employeeService.findAll()).thenReturn(List.of(employee("emp-1")));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("emp-1"))
                .andExpect(jsonPath("$[0].department").value("Engineering"));
    }

    @Test
    void findByIdReturnsNotFound() throws Exception {
        when(employeeService.findById("missing")).thenThrow(new EmployeeNotFoundException("missing"));

        mockMvc.perform(get("/api/employees/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Employee not found: missing"));
    }

    @Test
    void updateReturnsUpdatedEmployee() throws Exception {
        when(employeeService.update(eq("emp-1"), any(EmployeeRequest.class))).thenReturn(employee("emp-1"));

        mockMvc.perform(put("/api/employees/emp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Engineer"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/employees/emp-1"))
                .andExpect(status().isNoContent());

        verify(employeeService).delete("emp-1");
    }

    @Test
    void deleteReturnsNotFoundWhenEmployeeIsMissing() throws Exception {
        doThrow(new EmployeeNotFoundException("missing")).when(employeeService).delete("missing");

        mockMvc.perform(delete("/api/employees/missing"))
                .andExpect(status().isNotFound());
    }

    private static String validBody() {
        return """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "ada@example.com",
                  "department": "Engineering",
                  "title": "Engineer",
                  "salary": 120000.50
                }
                """;
    }

    private static Employee employee(String id) {
        return Employee.builder()
                .id(id)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .department("Engineering")
                .title("Engineer")
                .salary(new BigDecimal("120000.50"))
                .build();
    }
}
