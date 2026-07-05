package com.kaoshi.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExamMaterialGroupRequest(
        @NotBlank String title,
        String description,
        @NotNull Integer sortOrder,
        List<@Valid ExamMaterialFileRequest> files
) {
}
