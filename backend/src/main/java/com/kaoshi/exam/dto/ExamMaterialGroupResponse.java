package com.kaoshi.exam.dto;

import java.util.List;

public record ExamMaterialGroupResponse(
        Long id,
        String title,
        String description,
        Integer sortOrder,
        List<ExamMaterialFileResponse> files
) {
}
