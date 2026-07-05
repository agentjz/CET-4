package com.kaoshi.exam;

import com.kaoshi.common.api.ErrorCode;
import com.kaoshi.common.exception.BusinessException;
import com.kaoshi.exam.domain.Exam;
import com.kaoshi.exam.dto.ExamSaveRequest;
import com.kaoshi.exam.mapper.ExamMapper;

import java.util.List;

final class ExamPaperWorkflow {
    private final ExamMapper examMapper;

    ExamPaperWorkflow(ExamMapper examMapper) {
        this.examMapper = examMapper;
    }

    void ensureExamEditableAsDraft(Exam exam) {
        if ("CLOSED".equals(exam.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "考试已关闭，不能编辑草稿");
        }
        if (!"DRAFT".equals(exam.getStatus())
                && (examMapper.countAttemptsByExam(exam.getId()) > 0 || examMapper.countResultsByExam(exam.getId()) > 0)) {
            throw new BusinessException(ErrorCode.CONFLICT, "考试已有作答或成绩记录，不能改回草稿");
        }
    }

    void ensureExamPublishable(Exam exam) {
        if ("CLOSED".equals(exam.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "考试已关闭，不能发布");
        }
        if ("PUBLISHED".equals(exam.getStatus())
                && (examMapper.countAttemptsByExam(exam.getId()) > 0 || examMapper.countResultsByExam(exam.getId()) > 0)) {
            throw new BusinessException(ErrorCode.CONFLICT, "考试已有作答或成绩记录，不能重新发布快照");
        }
    }

    void clearPublishedQuestionsIfPresent(Long examId) {
        if (examMapper.countPublishedQuestions(examId) == 0) {
            return;
        }
        examMapper.deletePublishedAttachments(examId);
        examMapper.deletePublishedOptions(examId);
        examMapper.deletePublishedAnswerLabels(examId);
        examMapper.deletePublishedQuestions(examId);
    }

    void validateDraftBasics(ExamSaveRequest request) {
        if (!List.of("PAGED", "ALL").contains(request.displayMode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题目展示方式不合法");
        }
        if (!List.of("FIXED", "RANDOM").contains(request.questionOrderMode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题目顺序规则不合法");
        }
        if (!List.of("PUBLIC", "DEPARTMENT").contains(request.openType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "开放范围不合法");
        }
        if (!List.of("STRUCTURED", "ANSWER_SHEET").contains(request.examMode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "考试模式不合法");
        }
        if (Boolean.TRUE.equals(request.timeLimit()) && !request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "考试结束时间必须晚于开始时间");
        }
        validateDepartments(request.departmentIds());
    }

    void validatePublishBasics(Exam exam) {
        if ("DEPARTMENT".equals(exam.getOpenType()) && examMapper.findExamDepartmentIds(exam.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "部门开放必须选择部门");
        }
        if (Boolean.TRUE.equals(exam.getTimeLimit()) && !exam.getEndTime().isAfter(exam.getStartTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "考试结束时间必须晚于开始时间");
        }
    }

    void fillExam(Exam exam, ExamSaveRequest request, String status) {
        exam.setTitle(request.title());
        exam.setDescription(request.description());
        exam.setQualifyScore(request.qualifyScore());
        exam.setStartTime(request.startTime());
        exam.setEndTime(request.endTime());
        exam.setDurationMinutes(request.durationMinutes());
        exam.setTimeLimit(request.timeLimit());
        exam.setAttemptLimit(request.attemptLimit());
        exam.setExamMode(request.examMode());
        exam.setDisplayMode(request.displayMode());
        exam.setQuestionOrderMode(request.questionOrderMode());
        exam.setOpenType(request.openType());
        exam.setStatus(status);
    }

    void replaceExamDepartments(Long examId, List<Long> departmentIds) {
        examMapper.deleteExamDepartments(examId);
        if (departmentIds == null) {
            return;
        }
        departmentIds.stream()
                .distinct()
                .forEach(departmentId -> examMapper.insertExamDepartment(examId, departmentId));
    }

    private void validateDepartments(List<Long> departmentIds) {
        if (departmentIds == null) {
            return;
        }
        for (Long departmentId : departmentIds.stream().distinct().toList()) {
            if (examMapper.countActiveDepartmentById(departmentId) == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "部门不存在或未启用");
            }
        }
    }
}
