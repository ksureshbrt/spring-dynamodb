package com.ksb.springdynamodb.dto;

import java.time.Instant;

public record FileMetadata(String key, long size, String contentType, Instant lastModified) {
}
