package com.kaoshi.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ExamBusinessFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void examLifecycleSavesDraftPublishesAndScoresFromAttemptSnapshot() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "阅读理解题库");
        long questionId = createSingleChoiceQuestion(token, bankId);

        String draftResponse = createDraftExam(token, bankId, """
                "title": "阅读理解模拟考试",
                "description": "用于端到端测试",
                "qualifyScore": 6,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": true,
                "attemptLimit": null,
                "displayMode": "PAGED",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(draftResponse).at("/data/id").asLong();

        mockMvc.perform(get("/api/admin/exams/{examId}", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.rules[0].bankId").value(bankId))
                .andExpect(jsonPath("$.data.rules[0].singleCount").value(1))
                .andExpect(jsonPath("$.data.rules[0].singleScore").value(10))
                .andExpect(jsonPath("$.data.questionOrderMode").value("FIXED"));

        mockMvc.perform(post("/api/admin/exams/{examId}/publish", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.questionCount").value(1))
                .andExpect(jsonPath("$.data.totalScore").value(10));

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayMode").value("PAGED"))
                .andExpect(jsonPath("$.data.questions[0].type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[0].attachments[0].mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.data.questions[0].attachments[0].fileUrl").value("/assets/quick.png"))
                .andExpect(jsonPath("$.data.questions[0].options.length()").value(3));

        updateQuestionToDifferentCorrectAnswer(token, bankId, questionId);

        mockMvc.perform(post("/api/exam/{examId}/submit", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]}
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScore").value(10))
                .andExpect(jsonPath("$.data.obtainedScore").value(10))
                .andExpect(jsonPath("$.data.correctCount").value(1));

        mockMvc.perform(post("/api/exam/{examId}/submit", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]}
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isConflict());

        String resultsResponse = mockMvc.perform(get("/api/admin/results")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].examTitle").value("阅读理解模拟考试"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long resultId = objectMapper.readTree(resultsResponse).at("/data/0/id").asLong();

        mockMvc.perform(get("/api/admin/results/{resultId}", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].stem").value("Which word means fast?"))
                .andExpect(jsonPath("$.data.questions[0].selectedLabels[0]").value("B"))
                .andExpect(jsonPath("$.data.questions[0].correctLabels[0]").value("B"))
                .andExpect(jsonPath("$.data.questions[0].analysis").value("Quick means fast."));

        mockMvc.perform(get("/api/exam/results/{resultId}", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].correct").value(true));
    }

    @Test
    void seededDemoExamUsesSimpleQuestionBankAudioAttachment() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/exam/{examId}/start", 1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("CET-4 四级考试平台演示"))
                .andExpect(jsonPath("$.data.examMode").value("STRUCTURED"))
                .andExpect(jsonPath("$.data.questions.length()").value(4))
                .andExpect(jsonPath("$.data.questions[0].type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[1].type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[2].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[3].type").value("WRITING"))
                .andExpect(jsonPath("$.data.questions[*].attachments[*].fileUrl").value(hasItems(
                        "/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3"
                )))
                .andExpect(jsonPath("$.data.questions[*].attachments[*].mediaType").value(hasItems("AUDIO")));
    }

    @Test
    void answerSheetExamSavesMaterialsPublishesAndScoresByAnswerCardSnapshot() throws Exception {
        String token = adminToken();

        String response = mockMvc.perform(post("/api/admin/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "答题卡试卷考试",
                                  "description": "用于验证试卷材料和答题卡分离",
                                  "qualifyScore": 10,
                                  "startTime": "2026-01-01T00:00:00",
                                  "endTime": "2026-12-31T23:59:59",
                                  "durationMinutes": 45,
                                  "timeLimit": false,
                                  "attemptLimit": null,
                                  "examMode": "ANSWER_SHEET",
                                  "displayMode": "ALL",
                                  "questionOrderMode": "FIXED",
                                  "openType": "PUBLIC",
                                  "departmentIds": [],
                                  "rules": [],
                                  "paperQuestions": [],
                                  "materialGroups": [
                                    {
                                      "title": "听力材料",
                                      "description": "上方试卷材料",
                                      "sortOrder": 10,
                                      "files": [
                                        {
                                          "sourceType": "LOCAL_ASSET",
                                          "displayName": "四级听力音频",
                                          "description": "听力材料文件",
                                          "fileName": "2023-03-cet4-listening.mp3",
                                          "fileUrl": "/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3",
                                          "mediaType": "AUDIO",
                                          "sortOrder": 10
                                        }
                                      ]
                                    }
                                  ],
                                  "answerCardItems": [
                                    {
                                      "questionNo": 1,
                                      "answerType": "SINGLE_CHOICE",
                                      "optionLabels": ["A", "B", "C", "D"],
                                      "correctLabels": ["B"],
                                      "score": 5,
                                      "sortOrder": 10
                                    },
                                    {
                                      "questionNo": 2,
                                      "answerType": "MULTIPLE_CHOICE",
                                      "optionLabels": ["A", "B", "C", "D"],
                                      "correctLabels": ["A", "C"],
                                      "score": 5,
                                      "sortOrder": 20
                                    },
                                    {
                                      "questionNo": 3,
                                      "answerType": "WRITING",
                                      "optionLabels": [],
                                      "correctLabels": [],
                                      "score": 10,
                                      "sortOrder": 30
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examMode").value("ANSWER_SHEET"))
                .andExpect(jsonPath("$.data.materialGroups[0].files[0].mediaType").value("AUDIO"))
                .andExpect(jsonPath("$.data.answerCardItems.length()").value(3))
                .andExpect(jsonPath("$.data.totalScore").value(20))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long examId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(post("/api/admin/exams/{examId}/publish", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.questionCount").value(3))
                .andExpect(jsonPath("$.data.totalScore").value(20));

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examMode").value("ANSWER_SHEET"))
                .andExpect(jsonPath("$.data.materialGroups[0].files[0].fileUrl").value("/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3"))
                .andExpect(jsonPath("$.data.questions[0].questionId").value(-1))
                .andExpect(jsonPath("$.data.questions[0].stem").value("第 1 题"))
                .andExpect(jsonPath("$.data.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.data.questions[2].type").value("WRITING"));

        String submitResponse = mockMvc.perform(post("/api/exam/{examId}/submit", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": -1, "selectedLabels": ["B"]},
                                    {"questionId": -2, "selectedLabels": ["A", "C"]},
                                    {"questionId": -3, "answerText": "Answer sheet writing response."}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScore").value(20))
                .andExpect(jsonPath("$.data.objectiveScore").value(10))
                .andExpect(jsonPath("$.data.obtainedScore").value(10))
                .andExpect(jsonPath("$.data.gradingStatus").value("PENDING_REVIEW"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long resultId = objectMapper.readTree(submitResponse).at("/data/id").asLong();

        mockMvc.perform(get("/api/admin/results/{resultId}", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[2].answerText").value("Answer sheet writing response."));
    }

    @Test
    void limitedAttemptExamRejectsRestartAfterSubmittedLimit() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "限定次数题库");
        long questionId = createSingleChoiceQuestion(token, bankId);

        String examResponse = createDraftExam(token, bankId, """
                "title": "限定次数考试",
                "description": "用于验证可考次数",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": 1,
                "displayMode": "PAGED",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(examResponse).at("/data/id").asLong();
        publishExam(token, examId);

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/exam/{examId}/submit", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]}
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void examDisplayAndRandomOrderAreConfiguredByAdminAndPersistedInAttempt() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "整卷随机题库");
        createSingleChoiceQuestion(token, bankId);
        createSingleChoiceQuestion(token, bankId);
        createMultipleChoiceQuestion(token, bankId);
        createMultipleChoiceQuestion(token, bankId);

        String examResponse = createDraftExam(token, bankId, 2, 2, """
                "title": "整卷显示考试",
                "description": "用于验证题目显示配置",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "ALL",
                "questionOrderMode": "RANDOM",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(examResponse).at("/data/id").asLong();
        publishExam(token, examId);

        String firstStart = mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayMode").value("ALL"))
                .andExpect(jsonPath("$.data.questions.length()").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondStart = mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(secondStart).at("/data/attemptId").asLong())
                .isEqualTo(objectMapper.readTree(firstStart).at("/data/attemptId").asLong());
        assertThat(objectMapper.readTree(secondStart).at("/data/questions").toString())
                .isEqualTo(objectMapper.readTree(firstStart).at("/data/questions").toString());
    }

    @Test
    void answerDraftIsSavedToBackendAndRestoredWhenExamRestarts() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "答案保存题库");
        long questionId = createSingleChoiceQuestion(token, bankId);

        String examResponse = createDraftExam(token, bankId, """
                "title": "答案保存读回考试",
                "description": "用于验证作答防丢",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "PAGED",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(examResponse).at("/data/id").asLong();
        publishExam(token, examId);

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].selectedLabels.length()").value(0));

        mockMvc.perform(post("/api/exam/{examId}/answers", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]}
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].selectedLabels[0]").value("B"));

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].selectedLabels[0]").value("B"));
    }

    @Test
    void writingQuestionSupportsAnswerSaveReviewResumeCompleteAndFinalLock() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "写作生命周期题库");
        long singleQuestionId = createSingleChoiceQuestion(token, bankId);
        long writingQuestionId = createWritingQuestion(token, bankId);

        String examResponse = createDraftExam(token, bankId, 1, 0, 1, """
                "title": "写作阅卷考试",
                "description": "用于验证主观题阅卷闭环",
                "qualifyScore": 20,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "ALL",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(examResponse).at("/data/id").asLong();
        publishExam(token, examId);

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[1].type").value("WRITING"))
                .andExpect(jsonPath("$.data.questions[1].options.length()").value(0));

        mockMvc.perform(post("/api/exam/{examId}/answers", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionId": %d, "answerText": "Legacy single question save must fail."}
                                """.formatted(writingQuestionId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/exam/{examId}/answers", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "answerText": "Partial snapshot must fail."}
                                  ]
                                }
                                """.formatted(writingQuestionId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/exam/{examId}/answers", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": []},
                                    {"questionId": %d, "answerText": "This is a saved writing answer."}
                                  ]
                                }
                                """.formatted(singleQuestionId, writingQuestionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[1].answerText").value("This is a saved writing answer."));

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[1].answerText").value("This is a saved writing answer."));

        String submitResponse = mockMvc.perform(post("/api/exam/{examId}/submit", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]},
                                    {"questionId": %d, "answerText": "This is the final writing answer."}
                                  ]
                                }
                                """.formatted(singleQuestionId, writingQuestionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.objectiveScore").value(10))
                .andExpect(jsonPath("$.data.subjectiveScore").value(0))
                .andExpect(jsonPath("$.data.obtainedScore").value(10))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long resultId = objectMapper.readTree(submitResponse).at("/data/id").asLong();

        mockMvc.perform(get("/api/admin/results/{resultId}", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.questions[1].answerText").value("This is the final writing answer."))
                .andExpect(jsonPath("$.data.questions[1].reviewedAt").doesNotExist());

        mockMvc.perform(post("/api/admin/results/{resultId}/complete-review", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("仍有主观题未完成评分")));

        mockMvc.perform(post("/api/admin/results/{resultId}/questions/{questionId}/review", resultId, writingQuestionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score": 12, "comment": "结构清晰"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.subjectiveScore").value(12))
                .andExpect(jsonPath("$.data.obtainedScore").value(22))
                .andExpect(jsonPath("$.data.questions[1].reviewComment").value("结构清晰"))
                .andExpect(jsonPath("$.data.questions[1].reviewedAt").exists());

        mockMvc.perform(get("/api/admin/results/{resultId}", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.questions[1].reviewComment").value("结构清晰"));

        mockMvc.perform(post("/api/admin/results/{resultId}/complete-review", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingStatus").value("FINAL"))
                .andExpect(jsonPath("$.data.subjectiveScore").value(12))
                .andExpect(jsonPath("$.data.obtainedScore").value(22))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.reviewedAt").exists());

        mockMvc.perform(post("/api/admin/results/{resultId}/questions/{questionId}/review", resultId, writingQuestionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score": 1, "comment": "不应覆盖"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("已完成阅卷")));

        mockMvc.perform(post("/api/admin/results/{resultId}/complete-review", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("不能重复完成阅卷")));
    }

    @Test
    void manualPaperQuestionOrderIsSavedAndUsedByPublishedSnapshot() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "手工排序题库");
        long singleQuestionId = createSingleChoiceQuestion(token, bankId);
        long multipleQuestionId = createMultipleChoiceQuestion(token, bankId);

        String response = mockMvc.perform(post("/api/admin/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "手工排序考试",
                                  "description": "用于验证题目明细排序",
                                  "qualifyScore": 0,
                                  "startTime": "2026-01-01T00:00:00",
                                  "endTime": "2026-12-31T23:59:59",
                                  "durationMinutes": 20,
                                  "timeLimit": false,
                                  "attemptLimit": null,
                                  "examMode": "STRUCTURED",
                                  "displayMode": "ALL",
                                  "questionOrderMode": "FIXED",
                                  "openType": "PUBLIC",
                                  "departmentIds": [],
                                  "rules": [
                                    {
                                      "bankId": %d,
                                      "singleCount": 1,
                                      "singleScore": 3,
                                      "multipleCount": 1,
                                      "multipleScore": 7,
                                      "writingCount": 0,
                                      "writingScore": 0
                                    }
                                  ],
                                  "paperQuestions": [
                                    {"questionId": %d, "score": 7, "sortOrder": 10},
                                    {"questionId": %d, "score": 3, "sortOrder": 20}
                                  ],
                                  "materialGroups": [],
                                  "answerCardItems": []
                                }
                                """.formatted(bankId, multipleQuestionId, singleQuestionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paperQuestions[0].questionId").value(multipleQuestionId))
                .andExpect(jsonPath("$.data.paperQuestions[1].questionId").value(singleQuestionId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long examId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(post("/api/admin/exams/{examId}/publish", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(2))
                .andExpect(jsonPath("$.data.totalScore").value(10));

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].questionId").value(multipleQuestionId))
                .andExpect(jsonPath("$.data.questions[0].score").value(7))
                .andExpect(jsonPath("$.data.questions[1].questionId").value(singleQuestionId))
                .andExpect(jsonPath("$.data.questions[1].score").value(3));
    }

    @Test
    void draftPaperQuestionsKeepSnapshotUntilPaperIsExplicitlyRegenerated() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "草稿快照题库");
        long questionId = createSingleChoiceQuestion(token, bankId);

        String draftResponse = createDraftExam(token, bankId, """
                "title": "草稿快照考试",
                "description": "用于验证题库变更不会魔法更新试卷",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "ALL",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(draftResponse).at("/data/id").asLong();

        updateQuestionToDifferentCorrectAnswer(token, bankId, questionId);

        mockMvc.perform(get("/api/admin/exams/{examId}", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paperQuestions[0].stem").value("Which word means fast?"));

        publishExam(token, examId);

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].stem").value("Which word means fast?"));

        mockMvc.perform(post("/api/exam/{examId}/submit", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]}
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obtainedScore").value(10));
    }

    @Test
    void examPaperOperationsSupportCopyDownloadAndRevokeBoundary() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "试卷操作题库");
        createSingleChoiceQuestion(token, bankId);

        String response = createDraftExam(token, bankId, """
                "title": "试卷操作考试",
                "description": "用于验证复制下载撤销",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "ALL",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(response).at("/data/id").asLong();
        publishExam(token, examId);

        mockMvc.perform(get("/api/admin/exams/{examId}/download", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
                        Sheet sheet = workbook.getSheetAt(0);
                        assertThat(sheet.getSheetName()).isEqualTo("试卷");
                        assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("顺序");
                        assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isNotBlank();
                        assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isNotBlank();
                    }
                });

        mockMvc.perform(post("/api/admin/exams/{examId}/copy", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.title").value("试卷操作考试 副本"))
                .andExpect(jsonPath("$.data.paperQuestions.length()").value(1));

        mockMvc.perform(post("/api/admin/exams/{examId}/revoke", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        publishExam(token, examId);
        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/exams/{examId}/revoke", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("已有作答记录")));
    }

    @Test
    void examDeleteAndDraftEditRespectAttemptLifecycleBoundaries() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "生命周期边界题库");
        createSingleChoiceQuestion(token, bankId);

        String draftResponse = createDraftExam(token, bankId, """
                "title": "可删除草稿考试",
                "description": "用于验证草稿删除",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "PAGED",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long draftExamId = objectMapper.readTree(draftResponse).at("/data/id").asLong();

        mockMvc.perform(delete("/api/admin/exams/{examId}", draftExamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/exams/{examId}", draftExamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        String publishedResponse = createDraftExam(token, bankId, """
                "title": "发布后改草稿考试",
                "description": "用于验证发布快照清理",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 20,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "PAGED",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long publishedExamId = objectMapper.readTree(publishedResponse).at("/data/id").asLong();
        publishExam(token, publishedExamId);

        mockMvc.perform(put("/api/admin/exams/{examId}", publishedExamId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "发布后改回草稿",
                                  "description": "保存草稿应取消旧发布状态",
                                  "qualifyScore": 0,
                                  "startTime": "2026-01-01T00:00:00",
                                  "endTime": "2026-12-31T23:59:59",
                                  "durationMinutes": 20,
                                  "timeLimit": false,
                                  "attemptLimit": null,
                                  "examMode": "STRUCTURED",
                                  "displayMode": "PAGED",
                                  "questionOrderMode": "FIXED",
                                  "openType": "PUBLIC",
                                  "departmentIds": [],
                                  "rules": [
                                    {
                                      "bankId": %d,
                                      "singleCount": 1,
                                      "singleScore": 10,
                                      "multipleCount": 0,
                                      "multipleScore": 0,
                                      "writingCount": 0,
                                      "writingScore": 0
                                    }
                                  ],
                                  "paperQuestions": [],
                                  "materialGroups": [],
                                  "answerCardItems": []
                                }
                                """.formatted(bankId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.title").value("发布后改回草稿"));

        mockMvc.perform(post("/api/exam/{examId}/start", publishedExamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("考试未发布")));

        publishExam(token, publishedExamId);
        mockMvc.perform(post("/api/exam/{examId}/start", publishedExamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/exams/{examId}", publishedExamId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "不应允许编辑",
                                  "description": "",
                                  "qualifyScore": 0,
                                  "startTime": "2026-01-01T00:00:00",
                                  "endTime": "2026-12-31T23:59:59",
                                  "durationMinutes": 20,
                                  "timeLimit": false,
                                  "attemptLimit": null,
                                  "examMode": "STRUCTURED",
                                  "displayMode": "PAGED",
                                  "questionOrderMode": "FIXED",
                                  "openType": "PUBLIC",
                                  "departmentIds": [],
                                  "rules": [
                                    {
                                      "bankId": %d,
                                      "singleCount": 1,
                                      "singleScore": 10,
                                      "multipleCount": 0,
                                      "multipleScore": 0,
                                      "writingCount": 0,
                                      "writingScore": 0
                                    }
                                  ],
                                  "paperQuestions": [],
                                  "materialGroups": [],
                                  "answerCardItems": []
                                }
                                """.formatted(bankId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("已有作答或成绩记录")));

        mockMvc.perform(post("/api/admin/exams/{examId}/publish", publishedExamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("不能重新发布快照")));

        mockMvc.perform(delete("/api/admin/exams/{examId}", publishedExamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("已有作答或成绩记录")));
    }

    @Test
    void serverLocksAttemptWhenDurationHasExpired() throws Exception {
        String token = adminToken();
        long bankId = createBank(token, "限时锁定题库");
        long questionId = createSingleChoiceQuestion(token, bankId);

        String examResponse = createDraftExam(token, bankId, """
                "title": "服务端限时考试",
                "description": "用于验证考试时长",
                "qualifyScore": 0,
                "startTime": "2026-01-01T00:00:00",
                "endTime": "2026-12-31T23:59:59",
                "durationMinutes": 1,
                "timeLimit": false,
                "attemptLimit": null,
                "displayMode": "PAGED",
                "questionOrderMode": "FIXED",
                "openType": "PUBLIC"
                """);
        long examId = objectMapper.readTree(examResponse).at("/data/id").asLong();
        publishExam(token, examId);

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                "update exam_attempts set started_at = ? where exam_id = ? and user_id = 1 and status = 'IN_PROGRESS'",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)),
                examId
        );

        mockMvc.perform(post("/api/exam/{examId}/answers", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]}
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/exam/{examId}/submit", examId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedLabels": ["B"]}
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obtainedScore").value(0));

        mockMvc.perform(post("/api/exam/{examId}/start", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void questionValidationRejectsSingleChoiceWithMultipleCorrectOptions() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/admin/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankId": 1,
                                  "type": "SINGLE_CHOICE",
                                  "stem": "Invalid single choice",
                                  "analysis": "",
                                  "difficulty": "EASY",
                                  "status": "ACTIVE",
                                  "options": [
                                    {"label": "A", "content": "one", "correct": true},
                                    {"label": "B", "content": "two", "correct": true}
                                  ],
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void questionCategoryLifecycleSupportsBankOwnershipAndEmptyDeletionOnly() throws Exception {
        String token = adminToken();
        String categoryName = "后端分类" + System.currentTimeMillis();

        String categoryResponse = mockMvc.perform(post("/api/admin/question-banks/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "分类生命周期测试",
                                  "sortOrder": 30
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(categoryName))
                .andExpect(jsonPath("$.data.sortOrder").value(30))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long categoryId = objectMapper.readTree(categoryResponse).at("/data/id").asLong();

        mockMvc.perform(put("/api/admin/question-banks/categories/{id}", categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s-更新",
                                  "description": "分类已更新",
                                  "sortOrder": 40
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(categoryName + "-更新"))
                .andExpect(jsonPath("$.data.description").value("分类已更新"));

        String bankName = "分类归属题库" + System.currentTimeMillis();
        mockMvc.perform(post("/api/admin/question-banks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "name": "%s",
                                  "description": "验证题库归属分类",
                                  "status": "ACTIVE"
                                }
                                """.formatted(categoryId, bankName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(categoryId))
                .andExpect(jsonPath("$.data.categoryName").value(categoryName + "-更新"))
                .andExpect(jsonPath("$.data.name").value(bankName));

        mockMvc.perform(delete("/api/admin/question-banks/categories/{id}", categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("分类下存在题库")));

        String emptyCategoryResponse = mockMvc.perform(post("/api/admin/question-banks/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s-空",
                                  "description": "",
                                  "sortOrder": 50
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long emptyCategoryId = objectMapper.readTree(emptyCategoryResponse).at("/data/id").asLong();

        mockMvc.perform(delete("/api/admin/question-banks/categories/{id}", emptyCategoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/question-banks/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(hasItems(categoryName + "-更新")));
    }

    @Test
    void fileUploadStoresRealMediaAndReturnsAttachmentPayload() throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "listening.mp3",
                "audio/mpeg",
                new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/admin/files")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("listening.mp3"))
                .andExpect(jsonPath("$.data.fileUrl").value(startsWith("/uploads/")))
                .andExpect(jsonPath("$.data.mediaType").value("AUDIO"));
    }

    @Test
    void questionExcelImportSupportsTemplateExportSuccessAndRowErrors() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/admin/questions/import-template")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).contains("spreadsheetml.sheet"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                questionWorkbook("四级样例题库", "AC")
        );
        mockMvc.perform(multipart("/api/admin/questions/import")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(0));

        mockMvc.perform(get("/api/admin/questions?page=1&size=20&keyword=Excel import listening question")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].stem").value("Excel import listening question"))
                .andExpect(jsonPath("$.data.records[0].attachments.length()").value(0));

        mockMvc.perform(get("/api/admin/question-banks?page=1&size=20&keyword=四级样例题库")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].questionCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].singleChoiceCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].multipleChoiceCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/admin/questions/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).contains("spreadsheetml.sheet"));

        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "questions-invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                questionWorkbook("四级样例题库", "A,C")
        );
        mockMvc.perform(multipart("/api/admin/questions/import")
                        .file(invalidFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value(containsString("请使用 AC")));
    }

    private long createBank(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/question-banks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1,
                                  "name": "%s",
                                  "description": "用于端到端测试",
                                  "status": "ACTIVE"
                                }
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long createSingleChoiceQuestion(String token, long bankId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankId": %d,
                                  "type": "SINGLE_CHOICE",
                                  "stem": "Which word means fast?",
                                  "analysis": "Quick means fast.",
                                  "difficulty": "EASY",
                                  "status": "ACTIVE",
                                  "options": [
                                    {"label": "A", "content": "slow", "correct": false},
                                    {"label": "B", "content": "quick", "correct": true},
                                    {"label": "C", "content": "late", "correct": false}
                                  ],
                                  "attachments": [
                                    {"fileName": "quick.png", "fileUrl": "/assets/quick.png", "mediaType": "IMAGE"}
                                  ]
                                }
                                """.formatted(bankId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options.length()").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long createWritingQuestion(String token, long bankId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankId": %d,
                                  "type": "WRITING",
                                  "stem": "Write about a memorable learning experience.",
                                  "analysis": "参考要点：结构完整，表达清晰。",
                                  "difficulty": "HARD",
                                  "status": "ACTIVE",
                                  "options": [],
                                  "attachments": []
                                }
                                """.formatted(bankId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WRITING"))
                .andExpect(jsonPath("$.data.options.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long createMultipleChoiceQuestion(String token, long bankId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankId": %d,
                                  "type": "MULTIPLE_CHOICE",
                                  "stem": "Which words are language skills?",
                                  "analysis": "Listening and speaking are language skills.",
                                  "difficulty": "HARD",
                                  "status": "ACTIVE",
                                  "options": [
                                    {"label": "A", "content": "listening", "correct": true},
                                    {"label": "B", "content": "speaking", "correct": true},
                                    {"label": "C", "content": "sleeping", "correct": false},
                                    {"label": "D", "content": "walking", "correct": false}
                                  ],
                                  "attachments": []
                                }
                                """.formatted(bankId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MULTIPLE_CHOICE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private void updateQuestionToDifferentCorrectAnswer(String token, long bankId, long questionId) throws Exception {
        mockMvc.perform(put("/api/admin/questions/{questionId}", questionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankId": %d,
                                  "type": "SINGLE_CHOICE",
                                  "stem": "Changed source question",
                                  "analysis": "Changed analysis.",
                                  "difficulty": "HARD",
                                  "status": "ACTIVE",
                                  "options": [
                                    {"label": "A", "content": "quick", "correct": true},
                                    {"label": "B", "content": "slow", "correct": false}
                                  ],
                                  "attachments": []
                                }
                                """.formatted(bankId)))
                .andExpect(status().isOk());
    }

    private String createDraftExam(String token, long bankId, String fields) throws Exception {
        return createDraftExam(token, bankId, 1, 0, fields);
    }

    private String createDraftExam(String token, long bankId, int singleCount, int multipleCount, String fields) throws Exception {
        return createDraftExam(token, bankId, singleCount, multipleCount, 0, fields);
    }

    private String createDraftExam(String token, long bankId, int singleCount, int multipleCount, int writingCount, String fields) throws Exception {
        int singleScore = singleCount == 0 ? 0 : 10;
        int multipleScore = multipleCount == 0 ? 0 : 10;
        int writingScore = writingCount == 0 ? 0 : 15;
        return mockMvc.perform(post("/api/admin/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  %s,
                                  "departmentIds": [],
                                  "examMode": "STRUCTURED",
                                  "rules": [
                                    {
                                      "bankId": %d,
                                      "singleCount": %d,
                                      "singleScore": %d,
                                      "multipleCount": %d,
                                      "multipleScore": %d,
                                      "writingCount": %d,
                                      "writingScore": %d
                                    }
                                  ],
                                  "paperQuestions": [],
                                  "materialGroups": [],
                                  "answerCardItems": []
                                }
                                """.formatted(fields, bankId, singleCount, singleScore, multipleCount, multipleScore, writingCount, writingScore)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void publishExam(String token, long examId) throws Exception {
        mockMvc.perform(post("/api/admin/exams/{examId}/publish", examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private byte[] questionWorkbook(String bankName, String answer) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("试题导入");
            Row header = sheet.createRow(0);
            String[] headers = {"题库名称", "题型", "难度", "题干", "选项A", "选项B", "选项C", "选项D", "正确答案", "解析"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(bankName);
            row.createCell(1).setCellValue("多选");
            row.createCell(2).setCellValue("困难");
            row.createCell(3).setCellValue("Excel import listening question");
            row.createCell(4).setCellValue("listen");
            row.createCell(5).setCellValue("speak");
            row.createCell(6).setCellValue("sleep");
            row.createCell(7).setCellValue("walk");
            row.createCell(8).setCellValue(answer);
            row.createCell(9).setCellValue("Listening and speaking are language skills.");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String adminToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        String token = root.at("/data/accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
