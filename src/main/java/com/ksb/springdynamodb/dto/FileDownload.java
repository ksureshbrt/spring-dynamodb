package com.ksb.springdynamodb.dto;

import java.io.InputStream;

public record FileDownload(FileMetadata metadata, InputStream content) {
}
