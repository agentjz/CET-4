package com.kaoshi.exam.mapper;

import com.kaoshi.exam.domain.Exam;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

interface ExamCoreMapper {
    @Select("select count(*) from exams where #{keyword} is null or title like #{keyword}")
    long countExams(@Param("keyword") String keyword);

    @Select("""
            select *
            from exams
            where #{keyword} is null or title like #{keyword}
            order by id desc
            limit #{size} offset #{offset}
            """)
    List<Exam> findExams(@Param("keyword") String keyword, @Param("size") int size, @Param("offset") int offset);

    @Select("select * from exams where id = #{id}")
    Exam findExamById(@Param("id") Long id);

    @Insert("""
            insert into exams (title, description, qualify_score, start_time, end_time, duration_minutes, time_limit, attempt_limit, exam_mode, display_mode, question_order_mode, open_type, status)
            values (#{title}, #{description}, #{qualifyScore}, #{startTime}, #{endTime}, #{durationMinutes}, #{timeLimit}, #{attemptLimit}, #{examMode}, #{displayMode}, #{questionOrderMode}, #{openType}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertExam(Exam exam);

    @Update("""
            update exams
            set title = #{title},
                description = #{description},
                qualify_score = #{qualifyScore},
                start_time = #{startTime},
                end_time = #{endTime},
                duration_minutes = #{durationMinutes},
                time_limit = #{timeLimit},
                attempt_limit = #{attemptLimit},
                exam_mode = #{examMode},
                display_mode = #{displayMode},
                question_order_mode = #{questionOrderMode},
                open_type = #{openType},
                status = #{status},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateExam(Exam exam);

    @Update("update exams set status = #{status}, updated_at = current_timestamp where id = #{examId}")
    int updateExamStatus(@Param("examId") Long examId, @Param("status") String status);

    @Select("select count(*) from exam_attempts where exam_id = #{examId}")
    int countAttemptsByExam(@Param("examId") Long examId);

    @Select("select count(*) from exam_results where exam_id = #{examId}")
    int countResultsByExam(@Param("examId") Long examId);

    @Delete("delete from exams where id = #{examId}")
    int deleteExam(@Param("examId") Long examId);

    @Select("select department_id from users where id = #{userId} and deleted_at is null")
    Long findUserDepartmentId(@Param("userId") Long userId);

    @Select("select count(*) from departments where id = #{departmentId} and status = 'ACTIVE'")
    int countActiveDepartmentById(@Param("departmentId") Long departmentId);

    @Delete("delete from exam_departments where exam_id = #{examId}")
    void deleteExamDepartments(@Param("examId") Long examId);

    @Insert("insert into exam_departments (exam_id, department_id) values (#{examId}, #{departmentId})")
    void insertExamDepartment(@Param("examId") Long examId, @Param("departmentId") Long departmentId);

    @Select("select department_id from exam_departments where exam_id = #{examId} order by department_id")
    List<Long> findExamDepartmentIds(@Param("examId") Long examId);

    @Select("select count(*) from question_banks where id = #{bankId} and status = 'ACTIVE'")
    int countActiveBankById(@Param("bankId") Long bankId);

    @Select("select name from question_banks where id = #{bankId}")
    String findBankName(@Param("bankId") Long bankId);

    @Delete("delete from exam_rules where exam_id = #{examId}")
    void deleteExamRules(@Param("examId") Long examId);

    @Insert("""
            insert into exam_rules (exam_id, bank_id, single_count, single_score, multiple_count, multiple_score, writing_count, writing_score, sort_order)
            values (#{examId}, #{bankId}, #{singleCount}, #{singleScore}, #{multipleCount}, #{multipleScore}, #{writingCount}, #{writingScore}, #{sortOrder})
            """)
    void insertExamRule(Map<String, Object> rule);

    @Select("select * from exam_rules where exam_id = #{examId} order by sort_order, id")
    List<Map<String, Object>> findExamRules(@Param("examId") Long examId);

    @Delete("delete from exam_material_files where exam_id = #{examId}")
    void deleteExamMaterialFiles(@Param("examId") Long examId);

    @Insert("""
            insert into exam_material_files (group_id, exam_id, source_type, display_name, description, file_name, file_url, media_type, sort_order)
            values (#{groupId}, #{examId}, #{sourceType}, #{displayName}, #{description}, #{fileName}, #{fileUrl}, #{mediaType}, #{sortOrder})
            """)
    void insertExamMaterialFile(Map<String, Object> material);

    @Delete("delete from exam_material_groups where exam_id = #{examId}")
    void deleteExamMaterialGroups(@Param("examId") Long examId);

    @Insert("""
            insert into exam_material_groups (exam_id, title, description, sort_order)
            values (#{examId}, #{title}, #{description}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertExamMaterialGroup(Map<String, Object> group);

    @Select("select * from exam_material_groups where exam_id = #{examId} order by sort_order, id")
    List<Map<String, Object>> findExamMaterialGroups(@Param("examId") Long examId);

    @Select("select * from exam_material_files where group_id = #{groupId} order by sort_order, id")
    List<Map<String, Object>> findExamMaterialFiles(@Param("groupId") Long groupId);

    @Select("select count(*) from exam_material_files where exam_id = #{examId}")
    int countExamMaterialFiles(@Param("examId") Long examId);

    @Delete("delete from exam_published_material_files where exam_id = #{examId}")
    void deletePublishedMaterialFiles(@Param("examId") Long examId);

    @Delete("delete from exam_published_material_groups where exam_id = #{examId}")
    void deletePublishedMaterialGroups(@Param("examId") Long examId);

    @Insert("""
            insert into exam_published_material_groups (exam_id, title, description, sort_order)
            values (#{examId}, #{title}, #{description}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertPublishedMaterialGroup(Map<String, Object> group);

    @Insert("""
            insert into exam_published_material_files (group_id, exam_id, source_type, display_name, description, file_name, file_url, media_type, sort_order)
            values (#{groupId}, #{examId}, #{sourceType}, #{displayName}, #{description}, #{fileName}, #{fileUrl}, #{mediaType}, #{sortOrder})
            """)
    void insertPublishedMaterialFile(Map<String, Object> material);

    @Select("select * from exam_published_material_groups where exam_id = #{examId} order by sort_order, id")
    List<Map<String, Object>> findPublishedMaterialGroups(@Param("examId") Long examId);

    @Select("select * from exam_published_material_files where group_id = #{groupId} order by sort_order, id")
    List<Map<String, Object>> findPublishedMaterialFiles(@Param("groupId") Long groupId);

    @Delete("delete from exam_answer_card_items where exam_id = #{examId}")
    void deleteExamAnswerCardItems(@Param("examId") Long examId);

    @Insert("""
            insert into exam_answer_card_items (exam_id, question_no, answer_type, option_labels, correct_labels, score, sort_order)
            values (#{examId}, #{questionNo}, #{answerType}, #{optionLabels}, #{correctLabels}, #{score}, #{sortOrder})
            """)
    void insertExamAnswerCardItem(Map<String, Object> item);

    @Select("select * from exam_answer_card_items where exam_id = #{examId} order by sort_order, id")
    List<Map<String, Object>> findExamAnswerCardItems(@Param("examId") Long examId);

    @Select("""
            select count(*)
            from questions
            where bank_id = #{bankId}
              and type = #{type}
              and status = 'ACTIVE'
            """)
    int countActiveQuestions(@Param("bankId") Long bankId, @Param("type") String type);

    @Select("select * from exams where status = 'PUBLISHED' order by start_time desc, id desc")
    List<Exam> findPublishedExams();

    @Select("""
            select e.*
            from exams e
            where e.status = 'PUBLISHED'
              and (
                e.open_type = 'PUBLIC'
                or exists (
                  select 1
                  from exam_departments ed
                  where ed.exam_id = e.id
                    and ed.department_id = #{departmentId}
                )
              )
            order by e.start_time desc, e.id desc
            """)
    List<Exam> findPublishedExamsByDepartment(@Param("departmentId") Long departmentId);
}
