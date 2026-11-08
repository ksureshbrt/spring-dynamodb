package com.ksb.springdynamodb.service;

import com.ksb.springdynamodb.dto.EmployeeRequest;
import com.ksb.springdynamodb.exception.EmployeeNotFoundException;
import com.ksb.springdynamodb.model.Employee;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private DynamoDbTemplate dynamoDbTemplate;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void createAssignsIdAndSavesEmployee() {
        when(dynamoDbTemplate.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee created = employeeService.create(request());

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(dynamoDbTemplate).save(captor.capture());
        assertThat(captor.getValue().getId()).isNotBlank();
        assertThat(created.getFirstName()).isEqualTo("Ada");
        assertThat(created.getEmail()).isEqualTo("ada@example.com");
        assertThat(created.getSalary()).isEqualByComparingTo("120000.50");
    }

    @Test
    void findByIdReturnsStoredEmployee() {
        Employee stored = stored("emp-1");
        when(dynamoDbTemplate.load(any(Key.class), eq(Employee.class))).thenReturn(stored);

        assertThat(employeeService.findById("emp-1")).isEqualTo(stored);
    }

    @Test
    void findByIdThrowsWhenEmployeeIsMissing() {
        when(dynamoDbTemplate.load(any(Key.class), eq(Employee.class))).thenReturn(null);

        assertThatThrownBy(() -> employeeService.findById("missing"))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessage("Employee not found: missing");
    }

    @Test
    void findAllReturnsScannedEmployees() {
        Employee stored = stored("emp-1");
        @SuppressWarnings("unchecked")
        PageIterable<Employee> pages = mock(PageIterable.class);
        @SuppressWarnings("unchecked")
        SdkIterable<Employee> items = mock(SdkIterable.class);
        when(dynamoDbTemplate.scanAll(Employee.class)).thenReturn(pages);
        when(pages.items()).thenReturn(items);
        when(items.stream()).thenReturn(Stream.of(stored));

        assertThat(employeeService.findAll()).containsExactly(stored);
    }

    @Test
    void updateReplacesExistingEmployee() {
        when(dynamoDbTemplate.load(any(Key.class), eq(Employee.class))).thenReturn(stored("emp-1"));
        when(dynamoDbTemplate.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee updated = employeeService.update("emp-1", request());

        assertThat(updated.getId()).isEqualTo("emp-1");
        assertThat(updated.getTitle()).isEqualTo("Engineer");
    }

    @Test
    void deleteRemovesExistingEmployee() {
        when(dynamoDbTemplate.load(any(Key.class), eq(Employee.class))).thenReturn(stored("emp-1"));

        employeeService.delete("emp-1");

        verify(dynamoDbTemplate).delete(any(Key.class), eq(Employee.class));
    }

    private static EmployeeRequest request() {
        return new EmployeeRequest(" Ada ", "Lovelace", " ada@example.com ", "Engineering", "Engineer", new BigDecimal("120000.50"));
    }

    private static Employee stored(String id) {
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
