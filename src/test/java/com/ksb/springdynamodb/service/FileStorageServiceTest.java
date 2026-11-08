package com.ksb.springdynamodb.service;

import com.ksb.springdynamodb.config.S3Properties;
import com.ksb.springdynamodb.dto.FileDownload;
import com.ksb.springdynamodb.dto.FileMetadata;
import com.ksb.springdynamodb.exception.FileAlreadyExistsInStorageException;
import com.ksb.springdynamodb.exception.FileNotFoundInStorageException;
import com.ksb.springdynamodb.exception.InvalidFileRequestException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    private static final String BUCKET = "test-bucket";

    private static final Instant LAST_MODIFIED = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private S3Template s3Template;

    @Mock
    private S3Client s3Client;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(s3Template, s3Client, new S3Properties(BUCKET, false));
    }

    @Test
    void uploadUsesOriginalFilenameWhenKeyIsMissing() {
        when(s3Template.objectExists(BUCKET, "report.pdf")).thenReturn(false);
        stubHead("report.pdf", 5, "application/pdf");

        FileMetadata uploaded = fileStorageService.upload(null, file("report.pdf", "application/pdf"));

        ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Template).upload(eq(BUCKET), eq("report.pdf"), any(InputStream.class), metadata.capture());
        assertThat(metadata.getValue().getContentType()).isEqualTo("application/pdf");
        assertThat(uploaded).isEqualTo(new FileMetadata("report.pdf", 5, "application/pdf", LAST_MODIFIED));
    }

    @Test
    void uploadUsesRequestedKeyWithoutLeadingSlash() {
        when(s3Template.objectExists(BUCKET, "docs/report.pdf")).thenReturn(false);
        stubHead("docs/report.pdf", 5, "application/pdf");

        FileMetadata uploaded = fileStorageService.upload("/docs/report.pdf", file("ignored.pdf", "application/pdf"));

        assertThat(uploaded.key()).isEqualTo("docs/report.pdf");
    }

    @Test
    void uploadDefaultsContentTypeToOctetStream() {
        when(s3Template.objectExists(BUCKET, "data.bin")).thenReturn(false);
        stubHead("data.bin", 5, "application/octet-stream");

        fileStorageService.upload(null, file("data.bin", null));

        ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Template).upload(eq(BUCKET), eq("data.bin"), any(InputStream.class), metadata.capture());
        assertThat(metadata.getValue().getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void uploadRejectsExistingKey() {
        when(s3Template.objectExists(BUCKET, "report.pdf")).thenReturn(true);

        assertThatThrownBy(() -> fileStorageService.upload(null, file("report.pdf", "application/pdf")))
                .isInstanceOf(FileAlreadyExistsInStorageException.class)
                .hasMessage("File already exists: report.pdf");
        verify(s3Template, never()).upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class));
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.upload(null, empty))
                .isInstanceOf(InvalidFileRequestException.class)
                .hasMessage("File must not be empty");
    }

    @Test
    void listMapsObjectsUnderPrefix() {
        ListObjectsV2Iterable pages = mock(ListObjectsV2Iterable.class);
        @SuppressWarnings("unchecked")
        SdkIterable<S3Object> contents = mock(SdkIterable.class);
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(pages);
        when(pages.contents()).thenReturn(contents);
        when(contents.stream()).thenReturn(Stream.of(
                S3Object.builder().key("docs/a.txt").size(3L).lastModified(LAST_MODIFIED).build()));

        assertThat(fileStorageService.list("docs/"))
                .containsExactly(new FileMetadata("docs/a.txt", 3, null, LAST_MODIFIED));

        ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2Paginator(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().prefix()).isEqualTo("docs/");
    }

    @Test
    void downloadReturnsContentAndMetadata() throws Exception {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength(5L)
                .contentType("text/plain")
                .lastModified(LAST_MODIFIED)
                .build();
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(response,
                AbortableInputStream.create(new ByteArrayInputStream("hello".getBytes())));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);

        FileDownload download = fileStorageService.download("notes.txt");

        assertThat(download.metadata()).isEqualTo(new FileMetadata("notes.txt", 5, "text/plain", LAST_MODIFIED));
        assertThat(download.content().readAllBytes()).isEqualTo("hello".getBytes());
    }

    @Test
    void downloadThrowsWhenFileIsMissing() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(noSuchKey());

        assertThatThrownBy(() -> fileStorageService.download("missing.txt"))
                .isInstanceOf(FileNotFoundInStorageException.class)
                .hasMessage("File not found: missing.txt");
    }

    @Test
    void replaceOverwritesExistingFile() {
        stubHead("notes.txt", 5, "text/plain");

        FileMetadata replaced = fileStorageService.replace("notes.txt", file("new.txt", "text/plain"));

        verify(s3Template).upload(eq(BUCKET), eq("notes.txt"), any(InputStream.class), any(ObjectMetadata.class));
        assertThat(replaced.key()).isEqualTo("notes.txt");
    }

    @Test
    void replaceThrowsWhenFileIsMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(noSuchKey());

        assertThatThrownBy(() -> fileStorageService.replace("missing.txt", file("new.txt", "text/plain")))
                .isInstanceOf(FileNotFoundInStorageException.class);
        verify(s3Template, never()).upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class));
    }

    @Test
    void deleteRemovesExistingFile() {
        stubHead("notes.txt", 5, "text/plain");

        fileStorageService.delete("notes.txt");

        verify(s3Template).deleteObject(BUCKET, "notes.txt");
    }

    @Test
    void deleteThrowsWhenFileIsMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(noSuchKey());

        assertThatThrownBy(() -> fileStorageService.delete("missing.txt"))
                .isInstanceOf(FileNotFoundInStorageException.class);
        verify(s3Template, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void deleteRejectsBlankKey() {
        assertThatThrownBy(() -> fileStorageService.delete("/"))
                .isInstanceOf(InvalidFileRequestException.class)
                .hasMessage("File key must not be blank");
    }

    private void stubHead(String key, long size, String contentType) {
        when(s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build()))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(size)
                        .contentType(contentType)
                        .lastModified(LAST_MODIFIED)
                        .build());
    }

    private static NoSuchKeyException noSuchKey() {
        return (NoSuchKeyException) NoSuchKeyException.builder().statusCode(404).message("missing").build();
    }

    private static MockMultipartFile file(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, "hello".getBytes());
    }
}
