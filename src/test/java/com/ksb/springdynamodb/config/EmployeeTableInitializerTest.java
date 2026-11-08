package com.ksb.springdynamodb.config;

import com.ksb.springdynamodb.model.Employee;
import io.awspring.cloud.dynamodb.DynamoDbTableNameResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeTableInitializerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbTableNameResolver tableNameResolver;

    @Mock
    private DynamoDbWaiter dynamoDbWaiter;

    @InjectMocks
    private EmployeeTableInitializer initializer;

    @Test
    void skipsCreationWhenTableExists() {
        when(tableNameResolver.resolve(Employee.class)).thenReturn("employee");

        initializer.run(null);

        verify(dynamoDbClient, never()).createTable(any(CreateTableRequest.class));
    }

    @Test
    void createsTableWhenMissing() {
        when(tableNameResolver.resolve(Employee.class)).thenReturn("employee");
        when(dynamoDbClient.describeTable(any(DescribeTableRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("missing").build());
        when(dynamoDbClient.waiter()).thenReturn(dynamoDbWaiter);

        initializer.run(null);

        verify(dynamoDbClient).createTable(any(CreateTableRequest.class));
        verify(dynamoDbWaiter).waitUntilTableExists(any(DescribeTableRequest.class));
    }
}
