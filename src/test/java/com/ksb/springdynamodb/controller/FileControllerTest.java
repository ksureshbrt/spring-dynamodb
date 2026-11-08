package com.ksb.springdynamodb.controller;

import com.ksb.springdynamodb.dto.FileDownload;
import com.ksb.springdynamodb.dto.FileMetadata;
import com.ksb.springdynamodb.exception.FileAlreadyExistsInStorageException;
import com.ksb.springdynamodb.exception.FileNotFoundInStorageException;
import com.ksb.springdynamodb.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final Instant LAST_MODIFIED = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorageService fileStorageService;

    @Test
    void uploadReturnsCreatedFile() throws Exception {
        when(fileStorageService.upload(isNull(), any(MultipartFile.class))).thenReturn(metadata("docs/report.pdf"));

        mockMvc.perform(multipart("/api/files").file(file()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/files/docs/report.pdf"))
                .andExpect(jsonPath("$.key").value("docs/report.pdf"))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void uploadPassesRequestedKey() throws Exception {
        when(fileStorageService.upload(eq("custom.txt"), any(MultipartFile.class))).thenReturn(metadata("custom.txt"));

        mockMvc.perform(multipart("/api/files").file(file()).param("key", "custom.txt"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("custom.txt"));
    }

    @Test
    void uploadReturnsConflictWhenFileExists() throws Exception {
        when(fileStorageService.upload(isNull(), any(MultipartFile.class)))
                .thenThrow(new FileAlreadyExistsInStorageException("report.pdf"));

        mockMvc.perform(multipart("/api/files").file(file()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("File already exists: report.pdf"));
    }

    @Test
    void uploadRejectsMissingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/files"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing multipart part: file"));
    }

    @Test
    void listReturnsFiles() throws Exception {
        when(fileStorageService.list("docs/")).thenReturn(List.of(metadata("docs/report.pdf")));

        mockMvc.perform(get("/api/files").param("prefix", "docs/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("docs/report.pdf"));
    }

    @Test
    void downloadStreamsFileContent() throws Exception {
        FileMetadata metadata = new FileMetadata("docs/notes.txt", 5, "text/plain", LAST_MODIFIED);
        when(fileStorageService.download("/docs/notes.txt"))
                .thenReturn(new FileDownload(metadata, new ByteArrayInputStream("hello".getBytes())));

        mockMvc.perform(get("/api/files/docs/notes.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().string("Content-Length", "5"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"notes.txt\"; filename*=UTF-8''notes.txt"))
                .andExpect(content().string("hello"));
    }

    @Test
    void downloadReturnsNotFound() throws Exception {
        when(fileStorageService.download("/missing.txt")).thenThrow(new FileNotFoundInStorageException("missing.txt"));

        mockMvc.perform(get("/api/files/missing.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("File not found: missing.txt"));
    }

    @Test
    void replaceReturnsUpdatedFile() throws Exception {
        when(fileStorageService.replace(eq("/docs/report.pdf"), any(MultipartFile.class)))
                .thenReturn(metadata("docs/report.pdf"));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/files/docs/report.pdf").file(file()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("docs/report.pdf"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/files/docs/report.pdf"))
                .andExpect(status().isNoContent());

        verify(fileStorageService).delete("/docs/report.pdf");
    }

    @Test
    void deleteReturnsNotFoundWhenFileIsMissing() throws Exception {
        doThrow(new FileNotFoundInStorageException("missing.txt")).when(fileStorageService).delete("/missing.txt");

        mockMvc.perform(delete("/api/files/missing.txt"))
                .andExpect(status().isNotFound());
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
    }

    private static FileMetadata metadata(String key) {
        return new FileMetadata(key, 5, "application/pdf", LAST_MODIFIED);
    }
}
