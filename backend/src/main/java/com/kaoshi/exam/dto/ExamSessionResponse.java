package com.kaoshi.exam.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExamSessionResponse(
        Long examId,
        Long attemptId,
        String title,
        Integer durationMinutes,
        String examMode,
        String displayMode,
        LocalDateTime startedAt,
        String attemptStatus,
        List<ExamMaterialGroupResponse> materialGroups,
        List<ExamQuestionResponse> questions
) {
}

