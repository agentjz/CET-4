package com.kaoshi.exam.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public record ExamResponse(
        Long id,
        BigDecimal totalScore,
        Integer questionCount,
        String title,
        String description,
        BigDecimal qualifyScore,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,
        Boolean timeLimit,
        Integer attemptLimit,
        String examMode,
        String displayMode,
        String questionOrderMode,
        String openType,
        List<Long> departmentIds,
        List<ExamRuleResponse> rules,
        List<ExamPaperQuestionResponse> paperQuestions,
        List<ExamMaterialGroupResponse> materialGroups,
        List<ExamAnswerCardItemResponse> answerCardItems,
        String status
) {
}

