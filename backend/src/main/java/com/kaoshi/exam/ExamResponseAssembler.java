package com.kaoshi.exam;

import com.kaoshi.exam.domain.Exam;
import com.kaoshi.exam.dto.ExamPaperQuestionResponse;
import com.kaoshi.exam.dto.ExamMaterialFileResponse;
import com.kaoshi.exam.dto.ExamMaterialGroupResponse;
import com.kaoshi.exam.dto.ExamQuestionOptionResponse;
import com.kaoshi.exam.dto.ExamQuestionResponse;
import com.kaoshi.exam.dto.ExamResponse;
import com.kaoshi.exam.dto.ExamAnswerCardItemResponse;
import com.kaoshi.exam.dto.ExamResultDetailResponse;
import com.kaoshi.exam.dto.ExamResultQuestionResponse;
import com.kaoshi.exam.dto.ExamResultResponse;
import com.kaoshi.exam.dto.ExamRuleResponse;
import com.kaoshi.exam.dto.ExamSessionResponse;
import com.kaoshi.exam.mapper.ExamMapper;
import com.kaoshi.question.dto.QuestionAttachmentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.kaoshi.exam.ExamRowValues.booleanValue;
import static com.kaoshi.exam.ExamRowValues.dateTimeValue;
import static com.kaoshi.exam.ExamRowValues.dateTimeValueOrNull;
import static com.kaoshi.exam.ExamRowValues.decimalValue;
import static com.kaoshi.exam.ExamRowValues.intValue;
import static com.kaoshi.exam.ExamRowValues.longValue;
import static com.kaoshi.exam.ExamRowValues.normalizedLabels;
import static com.kaoshi.exam.ExamRowValues.splitLabels;
import static com.kaoshi.exam.ExamRowValues.stringValue;
import static com.kaoshi.exam.ExamRowValues.value;

final class ExamResponseAssembler {
    private final ExamMapper examMapper;

    ExamResponseAssembler(ExamMapper examMapper) {
        this.examMapper = examMapper;
    }

    ExamSessionResponse sessionResponse(Exam exam, Map<String, Object> attempt) {
        Long attemptId = longValue(value(attempt, "id"));
        return new ExamSessionResponse(
                exam.getId(),
                attemptId,
                exam.getTitle(),
                exam.getDurationMinutes(),
                exam.getExamMode(),
                exam.getDisplayMode(),
                dateTimeValue(value(attempt, "startedAt")),
                stringValue(value(attempt, "status")),
                examMapper.findPublishedMaterialGroups(exam.getId()).stream().map(this::toPublishedMaterialGroupResponse).toList(),
                examMapper.findAttemptQuestions(attemptId).stream()
                        .map(this::toExamQuestionResponse)
                        .toList()
        );
    }

    ExamResponse toResponse(Exam exam) {
        List<Map<String, Object>> rules = examMapper.findExamRules(exam.getId());
        List<Map<String, Object>> paperQuestions = examMapper.findDraftQuestions(exam.getId());
        BigDecimal totalScore = calculateRuleScore(rules);
        int questionCount = calculateRuleCount(rules);
        List<Map<String, Object>> answerCardItems = examMapper.findExamAnswerCardItems(exam.getId());
        if ("ANSWER_SHEET".equals(exam.getExamMode())) {
            totalScore = calculateAnswerCardScore(answerCardItems);
            questionCount = answerCardItems.size();
        }
        if (!paperQuestions.isEmpty()) {
            totalScore = calculatePaperScore(paperQuestions);
            questionCount = paperQuestions.size();
        }
        BigDecimal publishedScore = examMapper.findPublishedTotalScore(exam.getId());
        int publishedCount = examMapper.countPublishedQuestions(exam.getId());
        if (publishedCount > 0) {
            totalScore = publishedScore;
            questionCount = publishedCount;
        }
        return new ExamResponse(
                exam.getId(),
                totalScore,
                questionCount,
                exam.getTitle(),
                exam.getDescription(),
                exam.getQualifyScore(),
                exam.getStartTime(),
                exam.getEndTime(),
                exam.getDurationMinutes(),
                exam.getTimeLimit(),
                exam.getAttemptLimit(),
                exam.getExamMode(),
                exam.getDisplayMode(),
                exam.getQuestionOrderMode(),
                exam.getOpenType(),
                examMapper.findExamDepartmentIds(exam.getId()),
                rules.stream().map(this::toRuleResponse).toList(),
                paperQuestions.stream().map(this::toPaperQuestionResponse).toList(),
                examMapper.findExamMaterialGroups(exam.getId()).stream().map(this::toMaterialGroupResponse).toList(),
                answerCardItems.stream().map(this::toAnswerCardItemResponse).toList(),
                exam.getStatus()
        );
    }

    ExamResultResponse toResultResponse(Map<String, Object> row) {
        return new ExamResultResponse(
                longValue(value(row, "id")),
                longValue(value(row, "attemptId")),
                longValue(value(row, "examId")),
                stringValue(value(row, "examTitle")),
                longValue(value(row, "userId")),
                stringValue(value(row, "username")),
                stringValue(value(row, "userName")),
                stringValue(value(row, "departmentName")),
                decimalValue(value(row, "totalScore")),
                decimalValue(value(row, "obtainedScore")),
                decimalValue(value(row, "objectiveScore")),
                decimalValue(value(row, "subjectiveScore")),
                intValue(value(row, "correctCount")),
                intValue(value(row, "questionCount")),
                stringValue(value(row, "gradingStatus")),
                booleanValue(value(row, "passed")),
                dateTimeValue(value(row, "submittedAt"))
        );
    }

    ExamResultDetailResponse toResultDetailResponse(Map<String, Object> row) {
        Long attemptId = longValue(value(row, "attemptId"));
        return new ExamResultDetailResponse(
                longValue(value(row, "id")),
                attemptId,
                longValue(value(row, "examId")),
                stringValue(value(row, "examTitle")),
                longValue(value(row, "userId")),
                stringValue(value(row, "username")),
                stringValue(value(row, "userName")),
                stringValue(value(row, "departmentName")),
                decimalValue(value(row, "totalScore")),
                decimalValue(value(row, "obtainedScore")),
                decimalValue(value(row, "objectiveScore")),
                decimalValue(value(row, "subjectiveScore")),
                intValue(value(row, "correctCount")),
                intValue(value(row, "questionCount")),
                stringValue(value(row, "gradingStatus")),
                booleanValue(value(row, "passed")),
                dateTimeValue(value(row, "submittedAt")),
                dateTimeValueOrNull(value(row, "reviewedAt")),
                examMapper.findResultQuestions(attemptId).stream()
                        .map(this::toResultQuestionResponse)
                        .toList()
        );
    }

    BigDecimal calculatePaperScore(List<Map<String, Object>> paperQuestions) {
        return paperQuestions.stream()
                .map(question -> decimalValue(value(question, "score")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateAnswerCardScore(List<Map<String, Object>> items) {
        return items.stream()
                .map(item -> decimalValue(value(item, "score")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ExamQuestionResponse toExamQuestionResponse(Map<String, Object> row) {
        Long attemptQuestionId = longValue(value(row, "id"));
        return new ExamQuestionResponse(
                longValue(value(row, "sourceQuestionId")),
                stringValue(value(row, "type")),
                stringValue(value(row, "stem")),
                decimalValue(value(row, "score")),
                intValue(value(row, "displayOrder")),
                splitLabels(stringValue(value(row, "selectedLabels"))),
                stringValue(value(row, "answerText")),
                examMapper.findAttemptAttachments(attemptQuestionId).stream().map(this::toAttachmentResponse).toList(),
                examMapper.findAttemptOptions(attemptQuestionId).stream().map(this::toOptionResponse).toList()
        );
    }

    private ExamRuleResponse toRuleResponse(Map<String, Object> row) {
        Long bankId = longValue(value(row, "bankId"));
        return new ExamRuleResponse(
                longValue(value(row, "id")),
                bankId,
                examMapper.findBankName(bankId),
                intValue(value(row, "singleCount")),
                decimalValue(value(row, "singleScore")),
                intValue(value(row, "multipleCount")),
                decimalValue(value(row, "multipleScore")),
                intValue(value(row, "writingCount")),
                decimalValue(value(row, "writingScore")),
                intValue(value(row, "sortOrder"))
        );
    }

    private ExamPaperQuestionResponse toPaperQuestionResponse(Map<String, Object> row) {
        return new ExamPaperQuestionResponse(
                longValue(value(row, "questionId")),
                longValue(value(row, "bankId")),
                stringValue(value(row, "bankName")),
                stringValue(value(row, "type")),
                stringValue(value(row, "stem")),
                decimalValue(value(row, "score")),
                intValue(value(row, "sortOrder"))
        );
    }

    private ExamMaterialGroupResponse toMaterialGroupResponse(Map<String, Object> row) {
        Long groupId = longValue(value(row, "id"));
        return new ExamMaterialGroupResponse(
                groupId,
                stringValue(value(row, "title")),
                stringValue(value(row, "description")),
                intValue(value(row, "sortOrder")),
                examMapper.findExamMaterialFiles(groupId).stream().map(this::toMaterialFileResponse).toList()
        );
    }

    private ExamMaterialGroupResponse toPublishedMaterialGroupResponse(Map<String, Object> row) {
        Long groupId = longValue(value(row, "id"));
        return new ExamMaterialGroupResponse(
                groupId,
                stringValue(value(row, "title")),
                stringValue(value(row, "description")),
                intValue(value(row, "sortOrder")),
                examMapper.findPublishedMaterialFiles(groupId).stream().map(this::toMaterialFileResponse).toList()
        );
    }

    private ExamMaterialFileResponse toMaterialFileResponse(Map<String, Object> row) {
        return new ExamMaterialFileResponse(
                longValue(value(row, "id")),
                stringValue(value(row, "sourceType")),
                stringValue(value(row, "displayName")),
                stringValue(value(row, "description")),
                stringValue(value(row, "fileName")),
                stringValue(value(row, "fileUrl")),
                stringValue(value(row, "mediaType")),
                intValue(value(row, "sortOrder"))
        );
    }

    private ExamAnswerCardItemResponse toAnswerCardItemResponse(Map<String, Object> row) {
        return new ExamAnswerCardItemResponse(
                longValue(value(row, "id")),
                intValue(value(row, "questionNo")),
                stringValue(value(row, "answerType")),
                splitLabels(stringValue(value(row, "optionLabels"))),
                splitLabels(stringValue(value(row, "correctLabels"))),
                decimalValue(value(row, "score")),
                intValue(value(row, "sortOrder"))
        );
    }

    private ExamResultQuestionResponse toResultQuestionResponse(Map<String, Object> row) {
        Long attemptQuestionId = longValue(value(row, "attemptQuestionId"));
        return new ExamResultQuestionResponse(
                longValue(value(row, "questionId")),
                stringValue(value(row, "type")),
                stringValue(value(row, "stem")),
                stringValue(value(row, "analysis")),
                decimalValue(value(row, "score")),
                decimalValue(value(row, "obtainedScore")),
                intValue(value(row, "sortOrder")),
                splitLabels(stringValue(value(row, "selectedLabels"))),
                stringValue(value(row, "answerText")),
                normalizedLabels(examMapper.findAttemptCorrectLabels(attemptQuestionId)),
                booleanValue(value(row, "correct")),
                stringValue(value(row, "reviewComment")),
                stringValue(value(row, "reviewerName")),
                dateTimeValueOrNull(value(row, "reviewedAt")),
                examMapper.findAttemptAttachments(attemptQuestionId).stream().map(this::toAttachmentResponse).toList(),
                examMapper.findAttemptOptions(attemptQuestionId).stream().map(this::toOptionResponse).toList()
        );
    }

    private QuestionAttachmentResponse toAttachmentResponse(Map<String, Object> row) {
        return new QuestionAttachmentResponse(
                longValue(value(row, "id")),
                stringValue(value(row, "fileName")),
                stringValue(value(row, "fileUrl")),
                stringValue(value(row, "mediaType")),
                intValue(value(row, "sortOrder"))
        );
    }

    private ExamQuestionOptionResponse toOptionResponse(Map<String, Object> row) {
        return new ExamQuestionOptionResponse(
                longValue(value(row, "id")),
                stringValue(value(row, "label")),
                stringValue(value(row, "content")),
                intValue(value(row, "sortOrder"))
        );
    }

    private BigDecimal calculateRuleScore(List<Map<String, Object>> rules) {
        return rules.stream()
                .map(rule -> decimalValue(value(rule, "singleScore")).multiply(BigDecimal.valueOf(intValue(value(rule, "singleCount"))))
                        .add(decimalValue(value(rule, "multipleScore")).multiply(BigDecimal.valueOf(intValue(value(rule, "multipleCount")))))
                        .add(decimalValue(value(rule, "writingScore")).multiply(BigDecimal.valueOf(intValue(value(rule, "writingCount"))))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int calculateRuleCount(List<Map<String, Object>> rules) {
        return rules.stream()
                .mapToInt(rule -> intValue(value(rule, "singleCount")) + intValue(value(rule, "multipleCount")) + intValue(value(rule, "writingCount")))
                .sum();
    }
}
