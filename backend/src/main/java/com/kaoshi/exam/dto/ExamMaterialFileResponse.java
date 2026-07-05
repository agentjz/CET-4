package com.kaoshi.exam.dto;

public record ExamMaterialFileResponse(
        Long id,
        String sourceType,
        String displayName,
        String description,
        String fileName,
        String fileUrl,
        String mediaType,
        Integer sortOrder
) {
}
