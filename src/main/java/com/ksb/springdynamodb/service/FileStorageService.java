package com.ksb.springdynamodb.service;

import com.ksb.springdynamodb.config.S3Properties;
import com.ksb.springdynamodb.dto.FileDownload;
import com.ksb.springdynamodb.dto.FileMetadata;
import com.ksb.springdynamodb.exception.FileAlreadyExistsInStorageException;
import com.ksb.springdynamodb.exception.FileNotFoundInStorageException;
import com.ksb.springdynamodb.exception.InvalidFileRequestException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Template s3Template;

    private final S3Client s3Client;

    private final S3Properties properties;

    public FileMetadata upload(String requestedKey, MultipartFile file) {
        String key = resolveKey(requestedKey, file);
        if (s3Template.objectExists(properties.bucket(), key)) {
            throw new FileAlreadyExistsInStorageException(key);
        }
        return store(key, file);
    }

    public List<FileMetadata> list(String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(properties.bucket())
                .prefix(StringUtils.hasText(prefix) ? prefix : null)
                .build();
        return s3Client.listObjectsV2Paginator(request).contents().stream()
                .map(object -> new FileMetadata(object.key(), object.size(), null, object.lastModified()))
                .toList();
    }

    public FileMetadata getMetadata(String key) {
        String normalizedKey = normalizeKey(key);
        HeadObjectResponse response = callForKey(normalizedKey, () -> s3Client.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(normalizedKey)
                .build()));
        return new FileMetadata(normalizedKey, response.contentLength(), response.contentType(), response.lastModified());
    }

    public FileDownload download(String key) {
        String normalizedKey = normalizeKey(key);
        ResponseInputStream<GetObjectResponse> content = callForKey(normalizedKey, () -> s3Client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(normalizedKey)
                .build()));
        GetObjectResponse response = content.response();
        FileMetadata metadata = new FileMetadata(normalizedKey, response.contentLength(), response.contentType(), response.lastModified());
        return new FileDownload(metadata, content);
    }

    public FileMetadata replace(String key, MultipartFile file) {
        String normalizedKey = normalizeKey(key);
        requireFileContent(file);
        getMetadata(normalizedKey);
        return store(normalizedKey, file);
    }

    public void delete(String key) {
        String normalizedKey = normalizeKey(key);
        getMetadata(normalizedKey);
        s3Template.deleteObject(properties.bucket(), normalizedKey);
    }

    private FileMetadata store(String key, MultipartFile file) {
        ObjectMetadata metadata = ObjectMetadata.builder()
                .contentType(contentTypeOf(file))
                .contentLength(file.getSize())
                .build();
        try (InputStream inputStream = file.getInputStream()) {
            s3Template.upload(properties.bucket(), key, inputStream, metadata);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read uploaded file " + key, exception);
        }
        return getMetadata(key);
    }

    private static <T> T callForKey(String key, Supplier<T> call) {
        try {
            return call.get();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new FileNotFoundInStorageException(key);
            }
            throw exception;
        }
    }

    private static String resolveKey(String requestedKey, MultipartFile file) {
        requireFileContent(file);
        String key = StringUtils.hasText(requestedKey)
                ? requestedKey
                : StringUtils.getFilename(file.getOriginalFilename());
        return normalizeKey(key);
    }

    private static String normalizeKey(String key) {
        String normalized = key == null ? "" : key.strip().replaceFirst("^/+", "");
        if (normalized.isEmpty()) {
            throw new InvalidFileRequestException("File key must not be blank");
        }
        return normalized;
    }

    private static void requireFileContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileRequestException("File must not be empty");
        }
    }

    private static String contentTypeOf(MultipartFile file) {
        return StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
