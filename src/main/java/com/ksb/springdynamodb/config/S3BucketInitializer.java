package com.ksb.springdynamodb.config;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketNamespace;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.s3", name = "initialize-bucket", havingValue = "true")
public class S3BucketInitializer implements ApplicationRunner {

    private static final Pattern ACCOUNT_REGIONAL_SUFFIX = Pattern.compile("-\\d{12}-[a-z]{2}(-[a-z]+)+-\\d+-an$");

    private final S3Template s3Template;

    private final S3Client s3Client;

    private final S3Properties properties;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        String bucket = properties.bucket();
        if (bucketExists(bucket)) {
            log.info("S3 bucket {} already exists", bucket);
            return;
        }
        BucketNamespace namespace = isAccountRegional(bucket) ? BucketNamespace.ACCOUNT_REGIONAL : BucketNamespace.GLOBAL;
        log.info("Creating S3 bucket {} in {} namespace", bucket, namespace);
        s3Client.createBucket(
                CreateBucketRequest.builder()
                .bucket(bucket)
                .bucketNamespace(namespace)
                .build()
        );
        s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder().bucket(bucket).build());
        log.info("S3 bucket {} is ready", bucket);
    }

    static boolean isAccountRegional(String bucket) {
        return ACCOUNT_REGIONAL_SUFFIX.matcher(bucket).find();
    }

    private boolean bucketExists(String bucket) {
        try {
            return s3Template.bucketExists(bucket);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 403) {
                throw new IllegalStateException("Access denied to S3 bucket '" + bucket + "'. Bucket names are global, so it "
                        + "may be owned by another AWS account (set S3_BUCKET to a unique name), or your credentials lack "
                        + "s3:ListBucket on it. Set app.s3.initialize-bucket=false to skip this check.", exception);
            }
            throw exception;
        }
    }
}
