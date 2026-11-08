package com.ksb.springdynamodb.config;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.s3", name = "initialize-bucket", havingValue = "true")
public class S3BucketInitializer implements ApplicationRunner {

    private final S3Template s3Template;

    private final S3Properties properties;

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.bucket();
        if (s3Template.bucketExists(bucket)) {
            log.info("S3 bucket {} already exists", bucket);
            return;
        }
        log.info("Creating S3 bucket {}", bucket);
        s3Template.createBucket(bucket);
        log.info("S3 bucket {} is ready", bucket);
    }
}
