package com.kaoshi.exam;

import com.kaoshi.exam.dto.ExamMaterialFileRequest;
import com.kaoshi.exam.dto.ExamMaterialGroupRequest;
import com.kaoshi.exam.mapper.ExamMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kaoshi.exam.ExamRowValues.intValue;
import static com.kaoshi.exam.ExamRowValues.longValue;
import static com.kaoshi.exam.ExamRowValues.stringValue;
import static com.kaoshi.exam.ExamRowValues.value;

final class ExamMaterialWorkflow {
    private final ExamMapper examMapper;

    ExamMaterialWorkflow(ExamMapper examMapper) {
        this.examMapper = examMapper;
    }

    void clearPublishedMaterials(Long examId) {
        examMapper.deletePublishedMaterialFiles(examId);
        examMapper.deletePublishedMaterialGroups(examId);
    }

    void replaceMaterialGroups(Long examId, List<ExamMaterialGroupRequest> materialGroups) {
        examMapper.deleteExamMaterialFiles(examId);
        examMapper.deleteExamMaterialGroups(examId);
        int groupSort = 10;
        for (ExamMaterialGroupRequest groupRequest : materialGroups == null ? List.<ExamMaterialGroupRequest>of() : materialGroups) {
            Map<String, Object> group = new HashMap<>();
            group.put("examId", examId);
            group.put("title", groupRequest.title());
            group.put("description", groupRequest.description());
            group.put("sortOrder", groupRequest.sortOrder() == null ? groupSort : groupRequest.sortOrder());
            examMapper.insertExamMaterialGroup(group);
            Long groupId = longValue(value(group, "id"));
            replaceMaterialFiles(examId, groupId, groupRequest.files());
            groupSort += 10;
        }
    }

    void rebuildPublishedMaterials(Long examId) {
        clearPublishedMaterials(examId);
        for (Map<String, Object> sourceGroup : examMapper.findExamMaterialGroups(examId)) {
            Map<String, Object> group = new HashMap<>();
            group.put("examId", examId);
            group.put("title", stringValue(value(sourceGroup, "title")));
            group.put("description", stringValue(value(sourceGroup, "description")));
            group.put("sortOrder", intValue(value(sourceGroup, "sortOrder")));
            examMapper.insertPublishedMaterialGroup(group);
            Long publishedGroupId = longValue(value(group, "id"));
            Long sourceGroupId = longValue(value(sourceGroup, "id"));
            for (Map<String, Object> sourceFile : examMapper.findExamMaterialFiles(sourceGroupId)) {
                Map<String, Object> file = new HashMap<>();
                file.put("groupId", publishedGroupId);
                file.put("examId", examId);
                copyMaterialFileFields(file, sourceFile);
                examMapper.insertPublishedMaterialFile(file);
            }
        }
    }

    private void replaceMaterialFiles(Long examId, Long groupId, List<ExamMaterialFileRequest> files) {
        int fileSort = 10;
        for (ExamMaterialFileRequest fileRequest : files == null ? List.<ExamMaterialFileRequest>of() : files) {
            Map<String, Object> row = new HashMap<>();
            row.put("groupId", groupId);
            row.put("examId", examId);
            row.put("sourceType", fileRequest.sourceType());
            row.put("displayName", fileRequest.displayName());
            row.put("description", fileRequest.description());
            row.put("fileName", fileRequest.fileName());
            row.put("fileUrl", fileRequest.fileUrl());
            row.put("mediaType", fileRequest.mediaType());
            row.put("sortOrder", fileRequest.sortOrder() == null ? fileSort : fileRequest.sortOrder());
            examMapper.insertExamMaterialFile(row);
            fileSort += 10;
        }
    }

    private void copyMaterialFileFields(Map<String, Object> target, Map<String, Object> source) {
        target.put("sourceType", stringValue(value(source, "sourceType")));
        target.put("displayName", stringValue(value(source, "displayName")));
        target.put("description", stringValue(value(source, "description")));
        target.put("fileName", stringValue(value(source, "fileName")));
        target.put("fileUrl", stringValue(value(source, "fileUrl")));
        target.put("mediaType", stringValue(value(source, "mediaType")));
        target.put("sortOrder", intValue(value(source, "sortOrder")));
    }
}
