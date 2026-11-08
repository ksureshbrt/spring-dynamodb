package com.ksb.springdynamodb.config;

import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3BucketInitializerTest {

    @Mock
    private S3Template s3Template;

    private S3BucketInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new S3BucketInitializer(s3Template, new S3Properties("files", true));
    }

    @Test
    void skipsCreationWhenBucketExists() {
        when(s3Template.bucketExists("files")).thenReturn(true);

        initializer.run(null);

        verify(s3Template, never()).createBucket(anyString());
    }

    @Test
    void createsBucketWhenMissing() {
        when(s3Template.bucketExists("files")).thenReturn(false);

        initializer.run(null);

        verify(s3Template).createBucket("files");
    }
}
