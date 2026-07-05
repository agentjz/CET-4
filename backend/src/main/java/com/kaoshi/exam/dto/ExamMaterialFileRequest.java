package com.kaoshi.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExamMaterialFileRequest(
        @NotBlank String sourceType,
        @NotBlank String displayName,
        String description,
        @NotBlank String fileName,
        @NotBlank String fileUrl,
        @NotBlank String mediaType,
        @NotNull Integer sortOrder
) {
}
