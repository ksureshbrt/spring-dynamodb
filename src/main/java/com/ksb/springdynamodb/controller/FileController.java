package com.ksb.springdynamodb.controller;

import com.ksb.springdynamodb.dto.FileDownload;
import com.ksb.springdynamodb.dto.FileMetadata;
import com.ksb.springdynamodb.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FileMetadata> upload(@RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) String key) {
        FileMetadata uploaded = fileStorageService.upload(key, file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{key}")
                .buildAndExpand(uploaded.key())
                .toUri();
        return ResponseEntity.created(location).body(uploaded);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<FileMetadata> list(@RequestParam(required = false) String prefix) {
        return fileStorageService.list(prefix);
    }

    @GetMapping("/{*key}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String key) {
        FileDownload download = fileStorageService.download(key);
        FileMetadata metadata = download.metadata();
        String contentType = StringUtils.hasText(metadata.contentType())
                ? metadata.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(StringUtils.getFilename(metadata.key()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(metadata.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.content()));
    }

    @PutMapping(path = "/{*key}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public FileMetadata replace(@PathVariable String key, @RequestPart("file") MultipartFile file) {
        return fileStorageService.replace(key, file);
    }

    @DeleteMapping("/{*key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        fileStorageService.delete(key);
        return ResponseEntity.noContent().build();
    }
}
