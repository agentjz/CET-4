package com.kaoshi.exam;

import com.kaoshi.common.api.ErrorCode;
import com.kaoshi.common.exception.BusinessException;
import com.kaoshi.common.page.PageRequest;
import com.kaoshi.common.page.PageResponse;
import com.kaoshi.exam.domain.Exam;
import com.kaoshi.exam.dto.ExamAnswerSnapshotRequest;
import com.kaoshi.exam.dto.ExamResponse;
import com.kaoshi.exam.dto.ExamResultDetailResponse;
import com.kaoshi.exam.dto.ExamResultResponse;
import com.kaoshi.exam.dto.ExamSaveRequest;
import com.kaoshi.exam.dto.ExamSessionResponse;
import com.kaoshi.exam.dto.ExamSubmitRequest;
import com.kaoshi.exam.dto.WritingReviewRequest;
import com.kaoshi.exam.mapper.ExamMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kaoshi.exam.ExamRowValues.longValue;
import static com.kaoshi.exam.ExamRowValues.value;

@Service
public class ExamService {
    private static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

    private final ExamMapper examMapper;
    private final ExamResponseAssembler assembler;
    private final ExamPaperWorkflow paperWorkflow;
    private final ExamMaterialWorkflow materialWorkflow;
    private final ExamStructuredPaperWorkflow structuredPaperWorkflow;
    private final ExamAnswerSheetWorkflow answerSheetWorkflow;
    private final ExamAttemptWorkflow attemptWorkflow;
    private final ExamGradingWorkflow gradingWorkflow;
    private final ExamPaperExportService exportService;

    public ExamService(ExamMapper examMapper) {
        this.examMapper = examMapper;
        this.assembler = new ExamResponseAssembler(examMapper);
        this.paperWorkflow = new ExamPaperWorkflow(examMapper);
        this.materialWorkflow = new ExamMaterialWorkflow(examMapper);
        this.structuredPaperWorkflow = new ExamStructuredPaperWorkflow(examMapper, assembler);
        this.answerSheetWorkflow = new ExamAnswerSheetWorkflow(examMapper, materialWorkflow);
        this.attemptWorkflow = new ExamAttemptWorkflow(examMapper);
        this.gradingWorkflow = new ExamGradingWorkflow(examMapper);
        this.exportService = new ExamPaperExportService(examMapper);
    }

    public PageResponse<ExamResponse> page(PageRequest request) {
        long total = examMapper.countExams(request.keywordLike());
        List<ExamResponse> records = examMapper.findExams(request.keywordLike(), request.size(), request.offset())
                .stream()
                .map(assembler::toResponse)
                .toList();
        return new PageResponse<>(records, total, request.page(), request.size());
    }

    public ExamResponse detail(Long id) {
        return assembler.toResponse(findExam(id));
    }

    @Transactional
    public ExamResponse create(ExamSaveRequest request) {
        validateDraft(request);
        Exam exam = new Exam();
        paperWorkflow.fillExam(exam, request, "DRAFT");
        examMapper.insertExam(exam);
        paperWorkflow.replaceExamDepartments(exam.getId(), request.departmentIds());
        structuredPaperWorkflow.replaceRules(exam.getId(), request.rules());
        answerSheetWorkflow.replaceAnswerSheet(exam.getId(), request.materialGroups(), request.answerCardItems());
        structuredPaperWorkflow.replaceDraftQuestions(exam.getId(), request);
        return detail(exam.getId());
    }

    @Transactional
    public ExamResponse update(Long id, ExamSaveRequest request) {
        validateDraft(request);
        Exam exam = findExam(id);
        paperWorkflow.ensureExamEditableAsDraft(exam);
        clearPublishedSnapshot(id);
        paperWorkflow.fillExam(exam, request, "DRAFT");
        examMapper.updateExam(exam);
        paperWorkflow.replaceExamDepartments(id, request.departmentIds());
        structuredPaperWorkflow.replaceRules(id, request.rules());
        answerSheetWorkflow.replaceAnswerSheet(id, request.materialGroups(), request.answerCardItems());
        structuredPaperWorkflow.replaceDraftQuestions(id, request);
        return detail(id);
    }

    @Transactional
    public ExamResponse publish(Long id) {
        Exam exam = findExam(id);
        paperWorkflow.ensureExamPublishable(exam);
        validatePublish(exam);
        clearPublishedSnapshot(id);
        if ("ANSWER_SHEET".equals(exam.getExamMode())) {
            answerSheetWorkflow.rebuildPublishedSnapshot(id);
        } else {
            structuredPaperWorkflow.rebuildPublishedSnapshot(id);
        }
        examMapper.updateExamStatus(id, "PUBLISHED");
        return detail(id);
    }

    @Transactional
    public ExamResponse close(Long id) {
        findExam(id);
        examMapper.updateExamStatus(id, "CLOSED");
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        findExam(id);
        if (examMapper.countAttemptsByExam(id) > 0 || examMapper.countResultsByExam(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "考试已有作答或成绩记录，不能删除，请关闭考试保留历史");
        }
        clearPublishedSnapshot(id);
        examMapper.deleteDraftAttachments(id);
        examMapper.deleteDraftOptions(id);
        examMapper.deleteDraftAnswerLabels(id);
        examMapper.deleteDraftQuestions(id);
        examMapper.deleteExamMaterialFiles(id);
        examMapper.deleteExamMaterialGroups(id);
        examMapper.deleteExamAnswerCardItems(id);
        examMapper.deleteExamRules(id);
        examMapper.deleteExamDepartments(id);
        examMapper.deleteExam(id);
    }

    @Transactional
    public ExamResponse copy(Long id) {
        Exam source = findExam(id);
        Exam target = new Exam();
        target.setTitle(source.getTitle() + " 副本");
        target.setDescription(source.getDescription());
        target.setQualifyScore(source.getQualifyScore());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setDurationMinutes(source.getDurationMinutes());
        target.setTimeLimit(source.getTimeLimit());
        target.setAttemptLimit(source.getAttemptLimit());
        target.setExamMode(source.getExamMode());
        target.setDisplayMode(source.getDisplayMode());
        target.setQuestionOrderMode(source.getQuestionOrderMode());
        target.setOpenType(source.getOpenType());
        target.setStatus("DRAFT");
        examMapper.insertExam(target);
        paperWorkflow.replaceExamDepartments(target.getId(), examMapper.findExamDepartmentIds(id));
        structuredPaperWorkflow.copyRules(id, target.getId());
        answerSheetWorkflow.copyAnswerSheet(id, target.getId());
        if (!"ANSWER_SHEET".equals(source.getExamMode())) {
            structuredPaperWorkflow.copyPaperQuestions(id, target.getId());
        }
        return detail(target.getId());
    }

    public ResponseEntity<byte[]> download(Long id) {
        return exportService.download(findExam(id));
    }

    @Transactional
    public ExamResponse revoke(Long id) {
        Exam exam = findExam(id);
        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已发布考试可以撤销发布");
        }
        if (examMapper.countAttemptsByExam(id) > 0 || examMapper.countResultsByExam(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "考试已有作答记录，不能撤销发布");
        }
        clearPublishedSnapshot(id);
        examMapper.updateExamStatus(id, "DRAFT");
        return detail(id);
    }

    public List<ExamResponse> publishedExams() {
        return examMapper.findPublishedExams().stream().map(assembler::toResponse).toList();
    }

    public List<ExamResponse> publishedExams(Long userId) {
        Long departmentId = examMapper.findUserDepartmentId(userId);
        if (departmentId == null) {
            return examMapper.findPublishedExams().stream()
                    .filter(exam -> "PUBLIC".equals(exam.getOpenType()))
                    .map(assembler::toResponse)
                    .toList();
        }
        return examMapper.findPublishedExamsByDepartment(departmentId).stream().map(assembler::toResponse).toList();
    }

    @Transactional
    public ExamSessionResponse startExam(Long examId, Long userId) {
        Exam exam = findExam(examId);
        attemptWorkflow.ensureExamAvailable(exam);
        attemptWorkflow.ensureExamOpenToUser(exam, userId);
        Map<String, Object> attempt = examMapper.findInProgressAttempt(examId, userId);
        if (attempt == null) {
            attemptWorkflow.ensureAttemptLimit(exam, userId);
            attempt = new HashMap<>();
            attempt.put("examId", examId);
            attempt.put("userId", userId);
            examMapper.insertAttempt(attempt);
            attempt = examMapper.findInProgressAttempt(examId, userId);
            attemptWorkflow.createAttemptSnapshot(exam, longValue(value(attempt, "id")));
        } else if (examMapper.countAttemptQuestions(longValue(value(attempt, "id"))) == 0) {
            attemptWorkflow.createAttemptSnapshot(exam, longValue(value(attempt, "id")));
        }
        attemptWorkflow.ensureAttemptCanContinue(exam, attempt);
        return assembler.sessionResponse(exam, attempt);
    }

    @Transactional
    public ExamSessionResponse saveAnswerSnapshot(Long examId, Long userId, ExamAnswerSnapshotRequest request) {
        Exam exam = findExam(examId);
        attemptWorkflow.ensureExamAvailable(exam);
        attemptWorkflow.ensureExamOpenToUser(exam, userId);
        Map<String, Object> attempt = attemptWorkflow.findRunningAttempt(examId, userId);
        attemptWorkflow.ensureAttemptCanContinue(exam, attempt);
        attemptWorkflow.saveAnswerSnapshotForAttempt(longValue(value(attempt, "id")), request.answers());
        return assembler.sessionResponse(exam, attempt);
    }

    @Transactional
    public ExamResultResponse submit(Long examId, Long userId, ExamSubmitRequest request) {
        Exam exam = findExam(examId);
        attemptWorkflow.ensureExamAvailable(exam);
        attemptWorkflow.ensureExamOpenToUser(exam, userId);
        Map<String, Object> attempt = attemptWorkflow.findRunningAttempt(examId, userId);
        if (attemptWorkflow.isAttemptPastDeadline(exam, attempt, SUBMIT_GRACE)) {
            return gradingWorkflow.gradeAndLockAttempt(exam, attempt);
        }
        Long attemptId = longValue(value(attempt, "id"));
        attemptWorkflow.saveAnswerSnapshotForAttempt(attemptId, request.answers());
        return gradingWorkflow.gradeAndLockAttempt(exam, attempt);
    }

    public List<ExamResultResponse> adminResults(Long examId) {
        return examMapper.findResults(examId).stream().map(assembler::toResultResponse).toList();
    }

    public List<ExamResultResponse> userResults(Long userId) {
        return examMapper.findResultsByUser(userId).stream().map(assembler::toResultResponse).toList();
    }

    public ExamResultDetailResponse adminResultDetail(Long resultId) {
        return assembler.toResultDetailResponse(findResult(resultId));
    }

    @Transactional
    public ExamResultDetailResponse reviewWriting(Long resultId, Long questionId, Long reviewerId, WritingReviewRequest request) {
        gradingWorkflow.reviewWriting(resultId, questionId, reviewerId, request, findResult(resultId));
        return adminResultDetail(resultId);
    }

    @Transactional
    public ExamResultDetailResponse completeReview(Long resultId, Long reviewerId) {
        gradingWorkflow.completeReview(resultId, findResult(resultId));
        return adminResultDetail(resultId);
    }

    public ExamResultDetailResponse userResultDetail(Long resultId, Long userId) {
        Map<String, Object> result = findResult(resultId);
        if (!userId.equals(longValue(value(result, "userId")))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成绩不存在");
        }
        return assembler.toResultDetailResponse(result);
    }

    private Exam findExam(Long id) {
        Exam exam = examMapper.findExamById(id);
        if (exam == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
        }
        return exam;
    }

    private Map<String, Object> findResult(Long resultId) {
        Map<String, Object> result = examMapper.findResultById(resultId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成绩不存在");
        }
        return result;
    }

    private void validateDraft(ExamSaveRequest request) {
        paperWorkflow.validateDraftBasics(request);
        if ("ANSWER_SHEET".equals(request.examMode())) {
            answerSheetWorkflow.validateDraft(request);
            return;
        }
        structuredPaperWorkflow.validateRules(request.rules());
    }

    private void validatePublish(Exam exam) {
        paperWorkflow.validatePublishBasics(exam);
        if ("ANSWER_SHEET".equals(exam.getExamMode())) {
            answerSheetWorkflow.validatePublish(exam);
            return;
        }
        structuredPaperWorkflow.validatePublish(exam, examMapper.findExamRules(exam.getId()));
    }

    private void clearPublishedSnapshot(Long examId) {
        materialWorkflow.clearPublishedMaterials(examId);
        paperWorkflow.clearPublishedQuestionsIfPresent(examId);
    }
}
