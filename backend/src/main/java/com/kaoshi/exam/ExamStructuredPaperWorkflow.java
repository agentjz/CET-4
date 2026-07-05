package com.kaoshi.exam;

import com.kaoshi.common.api.ErrorCode;
import com.kaoshi.common.exception.BusinessException;
import com.kaoshi.exam.domain.Exam;
import com.kaoshi.exam.dto.ExamPaperQuestionRequest;
import com.kaoshi.exam.dto.ExamRuleRequest;
import com.kaoshi.exam.dto.ExamSaveRequest;
import com.kaoshi.exam.mapper.ExamMapper;
import com.kaoshi.question.QuestionType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.kaoshi.exam.ExamRowValues.decimalValue;
import static com.kaoshi.exam.ExamRowValues.intValue;
import static com.kaoshi.exam.ExamRowValues.longValue;
import static com.kaoshi.exam.ExamRowValues.stringValue;
import static com.kaoshi.exam.ExamRowValues.value;

final class ExamStructuredPaperWorkflow {
    private final ExamMapper examMapper;
    private final ExamResponseAssembler assembler;

    ExamStructuredPaperWorkflow(ExamMapper examMapper, ExamResponseAssembler assembler) {
        this.examMapper = examMapper;
        this.assembler = assembler;
    }

    void validateRules(List<ExamRuleRequest> rules) {
        if (rules == null) {
            return;
        }
        Set<Long> bankIds = new HashSet<>();
        for (ExamRuleRequest rule : rules) {
            if (!bankIds.add(rule.bankId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能重复选择题库");
            }
            if (examMapper.countActiveBankById(rule.bankId()) == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题库不存在或未启用");
            }
        }
    }

    void validatePublish(Exam exam, List<Map<String, Object>> rules) {
        for (Map<String, Object> rule : rules) {
            for (QuestionType type : QuestionType.ruleOrder()) {
                validateRuleAvailability(rule, type);
            }
        }
        List<Map<String, Object>> draftQuestions = examMapper.findDraftQuestions(exam.getId());
        if (draftQuestions.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "发布考试必须生成题目明细");
        }
        BigDecimal totalScore = assembler.calculatePaperScore(draftQuestions);
        int activeCount = examMapper.findDraftQuestionsForPublish(exam.getId()).size();
        if (activeCount != draftQuestions.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题目明细包含已停用试题");
        }
        if (totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "发布考试至少需要有效分值");
        }
        if (exam.getQualifyScore().compareTo(totalScore) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "及格分不能超过试卷总分");
        }
    }

    void replaceRules(Long examId, List<ExamRuleRequest> rules) {
        examMapper.deleteExamRules(examId);
        if (rules == null) {
            return;
        }
        int sort = 10;
        for (ExamRuleRequest request : rules) {
            Map<String, Object> rule = new HashMap<>();
            rule.put("examId", examId);
            rule.put("bankId", request.bankId());
            rule.put("singleCount", request.singleCount());
            rule.put("singleScore", request.singleScore());
            rule.put("multipleCount", request.multipleCount());
            rule.put("multipleScore", request.multipleScore());
            rule.put("writingCount", request.writingCount() == null ? 0 : request.writingCount());
            rule.put("writingScore", request.writingScore() == null ? BigDecimal.ZERO : request.writingScore());
            rule.put("sortOrder", sort);
            examMapper.insertExamRule(rule);
            sort += 10;
        }
    }

    void replaceDraftQuestions(Long examId, ExamSaveRequest request) {
        examMapper.deleteDraftAttachments(examId);
        examMapper.deleteDraftOptions(examId);
        examMapper.deleteDraftAnswerLabels(examId);
        examMapper.deleteDraftQuestions(examId);
        if ("ANSWER_SHEET".equals(request.examMode())) {
            return;
        }
        List<ExamPaperQuestionRequest> paperQuestions = request.paperQuestions();
        if (paperQuestions != null && !paperQuestions.isEmpty()) {
            replaceDraftQuestionsFromRequest(examId, paperQuestions);
            return;
        }
        replaceDraftQuestionsFromRules(examId, request.rules());
    }

    void copyRules(Long sourceExamId, Long targetExamId) {
        for (Map<String, Object> source : examMapper.findExamRules(sourceExamId)) {
            Map<String, Object> rule = new HashMap<>();
            rule.put("examId", targetExamId);
            rule.put("bankId", longValue(value(source, "bankId")));
            rule.put("singleCount", intValue(value(source, "singleCount")));
            rule.put("singleScore", decimalValue(value(source, "singleScore")));
            rule.put("multipleCount", intValue(value(source, "multipleCount")));
            rule.put("multipleScore", decimalValue(value(source, "multipleScore")));
            rule.put("writingCount", intValue(value(source, "writingCount")));
            rule.put("writingScore", decimalValue(value(source, "writingScore")));
            rule.put("sortOrder", intValue(value(source, "sortOrder")));
            examMapper.insertExamRule(rule);
        }
    }

    void copyPaperQuestions(Long sourceExamId, Long targetExamId) {
        List<Map<String, Object>> sourceQuestions = examMapper.findDraftQuestions(sourceExamId);
        if (sourceQuestions.isEmpty()) {
            sourceQuestions = examMapper.findPublishedQuestions(sourceExamId);
        }
        for (Map<String, Object> source : sourceQuestions) {
            Long sourceQuestionId = value(source, "questionId") == null
                    ? longValue(value(source, "sourceQuestionId"))
                    : longValue(value(source, "questionId"));
            insertDraftQuestionFromSource(targetExamId, sourceQuestionId, decimalValue(value(source, "score")), intValue(value(source, "sortOrder")));
        }
    }

    void rebuildPublishedSnapshot(Long examId) {
        for (Map<String, Object> source : examMapper.findDraftQuestionsForPublish(examId)) {
            Map<String, Object> question = new HashMap<>();
            question.put("examId", examId);
            question.put("sourceQuestionId", longValue(value(source, "questionId")));
            question.put("bankId", longValue(value(source, "bankId")));
            question.put("bankName", stringValue(value(source, "bankName")));
            question.put("type", stringValue(value(source, "type")));
            question.put("stem", stringValue(value(source, "stem")));
            question.put("analysis", stringValue(value(source, "analysis")));
            question.put("score", decimalValue(value(source, "score")));
            question.put("sortOrder", intValue(value(source, "sortOrder")));
            examMapper.insertPublishedQuestion(question);
            Long publishedQuestionId = longValue(value(question, "id"));
            examMapper.copyPublishedOptions(publishedQuestionId, longValue(value(source, "draftQuestionId")));
            examMapper.copyPublishedAnswerLabels(publishedQuestionId, longValue(value(source, "draftQuestionId")));
            examMapper.copyPublishedAttachments(publishedQuestionId, longValue(value(source, "draftQuestionId")));
        }
    }

    private BigDecimal validateRuleAvailability(Map<String, Object> rule, QuestionType type) {
        Long bankId = longValue(value(rule, "bankId"));
        int count = ruleCount(rule, type);
        BigDecimal score = ruleScore(rule, type);
        if (count == 0 && score.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (count <= 0 || score.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题量和分值必须同时大于 0");
        }
        int available = examMapper.countActiveQuestions(bankId, type.code());
        if (count > available) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题库题量不足：" + examMapper.findBankName(bankId));
        }
        return score.multiply(BigDecimal.valueOf(count));
    }

    private int ruleCount(Map<String, Object> rule, QuestionType type) {
        if (type == QuestionType.SINGLE_CHOICE) {
            return intValue(value(rule, "singleCount"));
        }
        if (type == QuestionType.MULTIPLE_CHOICE) {
            return intValue(value(rule, "multipleCount"));
        }
        return intValue(value(rule, "writingCount"));
    }

    private BigDecimal ruleScore(Map<String, Object> rule, QuestionType type) {
        if (type == QuestionType.SINGLE_CHOICE) {
            return decimalValue(value(rule, "singleScore"));
        }
        if (type == QuestionType.MULTIPLE_CHOICE) {
            return decimalValue(value(rule, "multipleScore"));
        }
        return decimalValue(value(rule, "writingScore"));
    }

    private void replaceDraftQuestionsFromRequest(Long examId, List<ExamPaperQuestionRequest> paperQuestions) {
        Set<Long> questionIds = new HashSet<>();
        int sort = 10;
        for (ExamPaperQuestionRequest request : paperQuestions.stream()
                .sorted((left, right) -> left.sortOrder().compareTo(right.sortOrder()))
                .toList()) {
            if (!questionIds.add(request.questionId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题目明细不能重复选择试题");
            }
            Map<String, Object> source = examMapper.findSourceQuestion(request.questionId());
            if (source == null || !"ACTIVE".equals(stringValue(value(source, "status")))) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题目不存在或未启用");
            }
            insertDraftQuestionFromSource(examId, request.questionId(), request.score(), sort);
            sort += 10;
        }
    }

    private void replaceDraftQuestionsFromRules(Long examId, List<ExamRuleRequest> rules) {
        if (rules == null) {
            return;
        }
        int sort = 10;
        for (ExamRuleRequest rule : rules) {
            sort = draftQuestions(examId, rule.bankId(), QuestionType.SINGLE_CHOICE.code(), rule.singleCount(), rule.singleScore(), sort);
            sort = draftQuestions(examId, rule.bankId(), QuestionType.MULTIPLE_CHOICE.code(), rule.multipleCount(), rule.multipleScore(), sort);
            sort = draftQuestions(
                    examId,
                    rule.bankId(),
                    QuestionType.WRITING.code(),
                    rule.writingCount() == null ? 0 : rule.writingCount(),
                    rule.writingScore() == null ? BigDecimal.ZERO : rule.writingScore(),
                    sort
            );
        }
    }

    private int draftQuestions(Long examId, Long bankId, String type, int count, BigDecimal score, int sort) {
        if (count <= 0) {
            return sort;
        }
        for (Map<String, Object> source : examMapper.findQuestionsForPublish(bankId, type, count)) {
            insertDraftQuestionFromSource(examId, longValue(value(source, "questionId")), score, sort);
            sort += 10;
        }
        return sort;
    }

    private void insertDraftQuestionFromSource(Long examId, Long sourceQuestionId, BigDecimal score, int sortOrder) {
        Map<String, Object> source = examMapper.findSourceQuestion(sourceQuestionId);
        if (source == null || !"ACTIVE".equals(stringValue(value(source, "status")))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题目不存在或未启用");
        }
        Map<String, Object> question = new HashMap<>();
        question.put("examId", examId);
        question.put("sourceQuestionId", sourceQuestionId);
        question.put("bankId", longValue(value(source, "bankId")));
        question.put("bankName", stringValue(value(source, "bankName")));
        question.put("type", stringValue(value(source, "type")));
        question.put("stem", stringValue(value(source, "stem")));
        question.put("analysis", stringValue(value(source, "analysis")));
        question.put("score", score);
        question.put("sortOrder", sortOrder);
        examMapper.insertDraftQuestion(question);
        Long draftQuestionId = longValue(value(question, "id"));
        examMapper.copyDraftOptionsFromSource(draftQuestionId, sourceQuestionId);
        examMapper.copyDraftAnswerLabelsFromSource(draftQuestionId, sourceQuestionId);
        examMapper.copyDraftAttachmentsFromSource(draftQuestionId, sourceQuestionId);
    }
}
