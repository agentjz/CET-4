package com.kaoshi.exam;

import com.kaoshi.common.api.ErrorCode;
import com.kaoshi.common.exception.BusinessException;
import com.kaoshi.exam.domain.Exam;
import com.kaoshi.exam.dto.ExamAnswerCardItemRequest;
import com.kaoshi.exam.dto.ExamMaterialFileRequest;
import com.kaoshi.exam.dto.ExamMaterialGroupRequest;
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
import static com.kaoshi.exam.ExamRowValues.normalizedLabels;
import static com.kaoshi.exam.ExamRowValues.splitLabels;
import static com.kaoshi.exam.ExamRowValues.stringValue;
import static com.kaoshi.exam.ExamRowValues.value;

final class ExamAnswerSheetWorkflow {
    private final ExamMapper examMapper;
    private final ExamMaterialWorkflow materialWorkflow;

    ExamAnswerSheetWorkflow(ExamMapper examMapper, ExamMaterialWorkflow materialWorkflow) {
        this.examMapper = examMapper;
        this.materialWorkflow = materialWorkflow;
    }

    void validateDraft(ExamSaveRequest request) {
        if (request.materialGroups() == null || request.materialGroups().isEmpty()
                || request.materialGroups().stream().allMatch(group -> group.files() == null || group.files().isEmpty())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "答题卡试卷必须上传或引用试卷材料");
        }
        validateMaterialGroups(request.materialGroups());
        if (request.answerCardItems() == null || request.answerCardItems().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "答题卡试卷必须配置答题卡");
        }
        Set<Integer> questionNos = new HashSet<>();
        for (ExamAnswerCardItemRequest item : request.answerCardItems()) {
            if (!questionNos.add(item.questionNo())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "答题卡题号不能重复");
            }
            QuestionType type = QuestionType.require(item.answerType());
            if (item.score().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "答题卡题目分值必须大于 0");
            }
            if (type.optionBased()) {
                if (item.optionLabels() == null || item.optionLabels().size() < 2) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "选择题至少需要两个选项");
                }
                validateAnswerCardLabels(type, normalizedLabels(item.optionLabels()), normalizedLabels(item.correctLabels()));
            }
        }
    }

    void validatePublish(Exam exam) {
        if (examMapper.countExamMaterialFiles(exam.getId()) == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "答题卡试卷必须上传或引用试卷材料");
        }
        List<Map<String, Object>> items = examMapper.findExamAnswerCardItems(exam.getId());
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "答题卡试卷必须配置答题卡");
        }
        BigDecimal totalScore = items.stream()
                .map(item -> decimalValue(value(item, "score")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "发布考试至少需要有效分值");
        }
        if (exam.getQualifyScore().compareTo(totalScore) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "及格分不能超过试卷总分");
        }
    }

    void replaceAnswerSheet(Long examId, List<ExamMaterialGroupRequest> materialGroups, List<ExamAnswerCardItemRequest> items) {
        materialWorkflow.replaceMaterialGroups(examId, materialGroups);
        examMapper.deleteExamAnswerCardItems(examId);
        int itemSort = 10;
        for (ExamAnswerCardItemRequest item : items == null ? List.<ExamAnswerCardItemRequest>of() : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("examId", examId);
            row.put("questionNo", item.questionNo());
            row.put("answerType", item.answerType());
            row.put("optionLabels", String.join(",", normalizedLabels(item.optionLabels())));
            row.put("correctLabels", String.join(",", normalizedLabels(item.correctLabels())));
            row.put("score", item.score());
            row.put("sortOrder", item.sortOrder() == null ? itemSort : item.sortOrder());
            examMapper.insertExamAnswerCardItem(row);
            itemSort += 10;
        }
    }

    void copyAnswerSheet(Long sourceExamId, Long targetExamId) {
        for (Map<String, Object> sourceGroup : examMapper.findExamMaterialGroups(sourceExamId)) {
            Map<String, Object> group = new HashMap<>(sourceGroup);
            group.put("examId", targetExamId);
            examMapper.insertExamMaterialGroup(group);
            Long targetGroupId = longValue(value(group, "id"));
            Long sourceGroupId = longValue(value(sourceGroup, "id"));
            for (Map<String, Object> material : examMapper.findExamMaterialFiles(sourceGroupId)) {
                Map<String, Object> row = new HashMap<>(material);
                row.put("groupId", targetGroupId);
                row.put("examId", targetExamId);
                examMapper.insertExamMaterialFile(row);
            }
        }
        for (Map<String, Object> item : examMapper.findExamAnswerCardItems(sourceExamId)) {
            Map<String, Object> row = new HashMap<>(item);
            row.put("examId", targetExamId);
            examMapper.insertExamAnswerCardItem(row);
        }
    }

    void rebuildPublishedSnapshot(Long examId) {
        materialWorkflow.rebuildPublishedMaterials(examId);
        for (Map<String, Object> item : examMapper.findExamAnswerCardItems(examId)) {
            Map<String, Object> question = new HashMap<>();
            question.put("examId", examId);
            question.put("sourceQuestionId", 0L - longValue(value(item, "questionNo")));
            question.put("bankId", 0L);
            question.put("bankName", "答题卡");
            question.put("type", stringValue(value(item, "answerType")));
            question.put("stem", "第 " + intValue(value(item, "questionNo")) + " 题");
            question.put("analysis", null);
            question.put("score", decimalValue(value(item, "score")));
            question.put("sortOrder", intValue(value(item, "sortOrder")));
            examMapper.insertPublishedQuestion(question);
            Long publishedQuestionId = longValue(value(question, "id"));
            List<String> labels = splitLabels(stringValue(value(item, "optionLabels")));
            List<String> correctLabels = splitLabels(stringValue(value(item, "correctLabels")));
            int sort = 10;
            for (String label : labels) {
                Map<String, Object> option = new HashMap<>();
                option.put("publishedQuestionId", publishedQuestionId);
                option.put("optionLabel", label);
                option.put("content", label);
                option.put("correct", correctLabels.contains(label));
                option.put("sortOrder", sort);
                examMapper.insertPublishedOption(option);
                sort += 10;
            }
            sort = 10;
            for (String label : correctLabels) {
                Map<String, Object> answer = new HashMap<>();
                answer.put("publishedQuestionId", publishedQuestionId);
                answer.put("answerLabel", label);
                answer.put("sortOrder", sort);
                examMapper.insertPublishedAnswerLabel(answer);
                sort += 10;
            }
        }
    }

    private void validateMaterialGroups(List<ExamMaterialGroupRequest> groups) {
        Set<Integer> groupSorts = new HashSet<>();
        for (ExamMaterialGroupRequest group : groups) {
            if (!groupSorts.add(group.sortOrder())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "材料分组排序不能重复");
            }
            Set<Integer> fileSorts = new HashSet<>();
            for (ExamMaterialFileRequest file : group.files() == null ? List.<ExamMaterialFileRequest>of() : group.files()) {
                if (!List.of("UPLOAD", "EXISTING_ATTACHMENT", "EXTERNAL_LINK", "LOCAL_ASSET").contains(file.sourceType())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "材料来源不合法");
                }
                if (!List.of("IMAGE", "AUDIO", "VIDEO", "FILE").contains(file.mediaType())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "材料类型不合法");
                }
                if (!fileSorts.add(file.sortOrder())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "材料文件排序不能重复");
                }
            }
        }
    }

    private void validateAnswerCardLabels(QuestionType type, List<String> optionLabels, List<String> correctLabels) {
        if (type.singleAnswer() && correctLabels.size() != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "单选题必须且只能有一个正确答案");
        }
        if (QuestionType.MULTIPLE_CHOICE == type && correctLabels.size() < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "多选题至少需要两个正确答案");
        }
        for (String label : correctLabels) {
            if (!optionLabels.contains(label)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "正确答案引用了不存在的选项：" + label);
            }
        }
    }
}
