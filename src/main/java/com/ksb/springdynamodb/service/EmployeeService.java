package com.ksb.springdynamodb.service;

import com.ksb.springdynamodb.dto.EmployeeRequest;
import com.ksb.springdynamodb.exception.EmployeeNotFoundException;
import com.ksb.springdynamodb.model.Employee;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final DynamoDbTemplate dynamoDbTemplate;

    public Employee create(EmployeeRequest request) {
        Employee employee = toEmployee(UUID.randomUUID().toString(), request);
        return dynamoDbTemplate.save(employee);
    }

    public List<Employee> findAll() {
        return dynamoDbTemplate.scanAll(Employee.class).items().stream().toList();
    }

    public Employee findById(String id) {
        Employee employee = dynamoDbTemplate.load(key(id), Employee.class);
        if (employee == null) {
            throw new EmployeeNotFoundException(id);
        }
        return employee;
    }

    public Employee update(String id, EmployeeRequest request) {
        findById(id);
        return dynamoDbTemplate.save(toEmployee(id, request));
    }

    public void delete(String id) {
        findById(id);
        dynamoDbTemplate.delete(key(id), Employee.class);
    }

    private static Key key(String id) {
        return Key.builder().partitionValue(id).build();
    }

    private static Employee toEmployee(String id, EmployeeRequest request) {
        return Employee.builder()
                .id(id)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(request.email().trim())
                .department(request.department().trim())
                .title(request.title().trim())
                .salary(request.salary())
                .build();
    }
}
