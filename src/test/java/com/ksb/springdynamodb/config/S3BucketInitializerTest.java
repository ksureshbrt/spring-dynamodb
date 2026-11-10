package com.ksb.springdynamodb.config;

import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketNamespace;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3BucketInitializerTest {

    private static final String ACCOUNT_REGIONAL_BUCKET = "files-111122223333-us-east-1-an";

    @Mock
    private S3Template s3Template;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Waiter s3Waiter;

    @Test
    void skipsCreationWhenBucketExists() {
        when(s3Template.bucketExists("files")).thenReturn(true);

        initializer("files").run(null);

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void createsGlobalBucketWhenMissing() {
        when(s3Template.bucketExists("files")).thenReturn(false);
        when(s3Client.waiter()).thenReturn(s3Waiter);

        initializer("files").run(null);

        assertThat(createdBucket().bucketNamespace()).isEqualTo(BucketNamespace.GLOBAL);
        verify(s3Waiter).waitUntilBucketExists(any(HeadBucketRequest.class));
    }

    @Test
    void createsAccountRegionalBucketWhenNameHasNamespaceSuffix() {
        when(s3Template.bucketExists(ACCOUNT_REGIONAL_BUCKET)).thenReturn(false);
        when(s3Client.waiter()).thenReturn(s3Waiter);

        initializer(ACCOUNT_REGIONAL_BUCKET).run(null);

        CreateBucketRequest request = createdBucket();
        assertThat(request.bucket()).isEqualTo(ACCOUNT_REGIONAL_BUCKET);
        assertThat(request.bucketNamespace()).isEqualTo(BucketNamespace.ACCOUNT_REGIONAL);
    }

    @Test
    void detectsAccountRegionalBucketNames() {
        assertThat(S3BucketInitializer.isAccountRegional("files-111122223333-us-east-1-an")).isTrue();
        assertThat(S3BucketInitializer.isAccountRegional("files-111122223333-ap-southeast-2-an")).isTrue();
        assertThat(S3BucketInitializer.isAccountRegional("files-an")).isFalse();
        assertThat(S3BucketInitializer.isAccountRegional("spring-dynamodb-files")).isFalse();
    }

    @Test
    void explainsAccessDeniedOnBucket() {
        when(s3Template.bucketExists("files"))
                .thenThrow(S3Exception.builder().statusCode(403).message("Forbidden").build());

        assertThatThrownBy(() -> initializer("files").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access denied to S3 bucket 'files'");
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    private S3BucketInitializer initializer(String bucket) {
        return new S3BucketInitializer(s3Template, s3Client, new S3Properties(bucket, true));
    }

    private CreateBucketRequest createdBucket() {
        ArgumentCaptor<CreateBucketRequest> request = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(request.capture());
        return request.getValue();
    }
}
