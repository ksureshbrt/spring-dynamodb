package com.ksb.springdynamodb.config;

import com.ksb.springdynamodb.model.Employee;
import io.awspring.cloud.dynamodb.DynamoDbTableNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.dynamodb", name = "initialize-schema", havingValue = "true", matchIfMissing = true)
public class EmployeeTableInitializer implements ApplicationRunner {

    private final DynamoDbClient dynamoDbClient;

    private final DynamoDbTableNameResolver tableNameResolver;

    @Override
    public void run(ApplicationArguments args) {
        String tableName = tableNameResolver.resolve(Employee.class);
        DescribeTableRequest describeRequest = DescribeTableRequest.builder().tableName(tableName).build();
        try {
            dynamoDbClient.describeTable(describeRequest);
            log.info("DynamoDB table {} already exists", tableName);
        } catch (ResourceNotFoundException exception) {
            log.info("Creating DynamoDB table {}", tableName);
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("id")
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("id")
                            .keyType(KeyType.HASH)
                            .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            dynamoDbClient.waiter().waitUntilTableExists(describeRequest);
            log.info("DynamoDB table {} is ready", tableName);
        }
    }
}
