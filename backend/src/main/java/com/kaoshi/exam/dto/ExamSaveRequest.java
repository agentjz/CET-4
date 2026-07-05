package com.kaoshi.exam.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public record ExamSaveRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("0.00") BigDecimal qualifyScore,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @Min(1) Integer durationMinutes,
        @NotNull Boolean timeLimit,
        @Min(1) Integer attemptLimit,
        @NotBlank String examMode,
        @NotBlank String displayMode,
        @NotBlank String questionOrderMode,
        @NotBlank String openType,
        List<Long> departmentIds,
        List<@Valid ExamRuleRequest> rules,
        List<@Valid ExamPaperQuestionRequest> paperQuestions,
        List<@Valid ExamMaterialGroupRequest> materialGroups,
        List<@Valid ExamAnswerCardItemRequest> answerCardItems
) {
}

