create table question_categories (
  id bigint primary key auto_increment,
  name varchar(64) not null,
  description varchar(255) null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint uk_question_categories_name unique (name)
);

create table question_banks (
  id bigint primary key auto_increment,
  category_id bigint not null,
  name varchar(128) not null,
  description varchar(500) null,
  status varchar(20) not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_question_banks_category foreign key (category_id) references question_categories (id),
  index idx_question_banks_category (category_id),
  index idx_question_banks_status (status)
);

create table questions (
  id bigint primary key auto_increment,
  bank_id bigint not null,
  type varchar(32) not null,
  stem text not null,
  analysis text null,
  difficulty varchar(20) not null,
  status varchar(20) not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_questions_bank foreign key (bank_id) references question_banks (id),
  index idx_questions_bank (bank_id),
  index idx_questions_type (type),
  index idx_questions_status (status)
);

create table question_options (
  id bigint primary key auto_increment,
  question_id bigint not null,
  option_label varchar(8) not null,
  content text not null,
  is_correct bit not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_question_options_question foreign key (question_id) references questions (id),
  constraint uk_question_options_label unique (question_id, option_label),
  index idx_question_options_question (question_id)
);

create table question_answer_labels (
  id bigint primary key auto_increment,
  question_id bigint not null,
  answer_label varchar(8) not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_question_answer_labels_question foreign key (question_id) references questions (id),
  constraint uk_question_answer_labels unique (question_id, answer_label),
  index idx_question_answer_labels_question (question_id)
);

create table question_attachments (
  id bigint primary key auto_increment,
  question_id bigint not null,
  file_name varchar(255) not null,
  file_url varchar(1000) not null,
  media_type varchar(32) not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_question_attachments_question foreign key (question_id) references questions (id),
  index idx_question_attachments_question (question_id)
);

create table exams (
  id bigint primary key auto_increment,
  title varchar(128) not null,
  description varchar(500) null,
  qualify_score decimal(7,2) not null default 0,
  start_time datetime not null,
  end_time datetime not null,
  duration_minutes int not null,
  time_limit bit not null default true,
  attempt_limit int null,
  exam_mode varchar(32) not null default 'STRUCTURED',
  display_mode varchar(20) not null default 'PAGED',
  question_order_mode varchar(20) not null default 'FIXED',
  open_type varchar(20) not null default 'PUBLIC',
  status varchar(20) not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  index idx_exams_status (status),
  index idx_exams_mode (exam_mode),
  index idx_exams_time (start_time, end_time)
);

create table exam_material_groups (
  id bigint primary key auto_increment,
  exam_id bigint not null,
  title varchar(255) not null,
  description text null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_material_groups_exam foreign key (exam_id) references exams (id),
  index idx_exam_material_groups_exam (exam_id)
);

create table exam_material_files (
  id bigint primary key auto_increment,
  group_id bigint not null,
  exam_id bigint not null,
  source_type varchar(32) not null default 'EXTERNAL_LINK',
  display_name varchar(255) not null,
  description text null,
  file_name varchar(255) not null,
  file_url varchar(1000) not null,
  media_type varchar(32) not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_material_files_group foreign key (group_id) references exam_material_groups (id),
  constraint fk_exam_material_files_exam foreign key (exam_id) references exams (id),
  index idx_exam_material_files_group (group_id),
  index idx_exam_material_files_exam (exam_id)
);

create table exam_published_material_groups (
  id bigint primary key auto_increment,
  exam_id bigint not null,
  title varchar(255) not null,
  description text null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_published_material_groups_exam foreign key (exam_id) references exams (id),
  index idx_exam_published_material_groups_exam (exam_id)
);

create table exam_published_material_files (
  id bigint primary key auto_increment,
  group_id bigint not null,
  exam_id bigint not null,
  source_type varchar(32) not null default 'EXTERNAL_LINK',
  display_name varchar(255) not null,
  description text null,
  file_name varchar(255) not null,
  file_url varchar(1000) not null,
  media_type varchar(32) not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_published_material_files_group foreign key (group_id) references exam_published_material_groups (id),
  constraint fk_exam_published_material_files_exam foreign key (exam_id) references exams (id),
  index idx_exam_published_material_files_group (group_id),
  index idx_exam_published_material_files_exam (exam_id)
);

create table exam_answer_card_items (
  id bigint primary key auto_increment,
  exam_id bigint not null,
  question_no int not null,
  answer_type varchar(32) not null,
  option_labels varchar(255) null,
  correct_labels varchar(255) null,
  score decimal(6,2) not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_answer_card_items_exam foreign key (exam_id) references exams (id),
  constraint uk_exam_answer_card_question unique (exam_id, question_no),
  index idx_exam_answer_card_items_exam (exam_id)
);

create table exam_rules (
  id bigint primary key auto_increment,
  exam_id bigint not null,
  bank_id bigint not null,
  single_count int not null default 0,
  single_score decimal(6,2) not null default 0,
  multiple_count int not null default 0,
  multiple_score decimal(6,2) not null default 0,
  writing_count int not null default 0,
  writing_score decimal(6,2) not null default 0,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_rules_exam foreign key (exam_id) references exams (id),
  constraint fk_exam_rules_bank foreign key (bank_id) references question_banks (id),
  constraint uk_exam_rules_bank unique (exam_id, bank_id),
  index idx_exam_rules_exam (exam_id)
);

create table exam_draft_questions (
  id bigint primary key auto_increment,
  exam_id bigint not null,
  source_question_id bigint not null,
  bank_id bigint not null,
  bank_name varchar(128) not null,
  type varchar(32) not null,
  stem text not null,
  analysis text null,
  score decimal(6,2) not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_draft_questions_exam foreign key (exam_id) references exams (id),
  constraint fk_exam_draft_questions_source foreign key (source_question_id) references questions (id),
  constraint uk_exam_draft_questions_source unique (exam_id, source_question_id),
  index idx_exam_draft_questions_exam (exam_id)
);

create table exam_draft_options (
  id bigint primary key auto_increment,
  draft_question_id bigint not null,
  option_label varchar(8) not null,
  content text not null,
  is_correct bit not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_draft_options_question foreign key (draft_question_id) references exam_draft_questions (id),
  index idx_exam_draft_options_question (draft_question_id)
);

create table exam_draft_answer_labels (
  id bigint primary key auto_increment,
  draft_question_id bigint not null,
  answer_label varchar(8) not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_draft_answer_labels_question foreign key (draft_question_id) references exam_draft_questions (id),
  index idx_exam_draft_answer_labels_question (draft_question_id)
);

create table exam_draft_attachments (
  id bigint primary key auto_increment,
  draft_question_id bigint not null,
  file_name varchar(255) not null,
  file_url varchar(1000) not null,
  media_type varchar(32) not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_draft_attachments_question foreign key (draft_question_id) references exam_draft_questions (id),
  index idx_exam_draft_attachments_question (draft_question_id)
);

create table exam_published_questions (
  id bigint primary key auto_increment,
  exam_id bigint not null,
  source_question_id bigint not null,
  bank_id bigint not null,
  bank_name varchar(128) not null,
  type varchar(32) not null,
  stem text not null,
  analysis text null,
  score decimal(6,2) not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_published_questions_exam foreign key (exam_id) references exams (id),
  index idx_exam_published_questions_exam (exam_id)
);

create table exam_published_options (
  id bigint primary key auto_increment,
  published_question_id bigint not null,
  option_label varchar(8) not null,
  content text not null,
  is_correct bit not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_published_options_question foreign key (published_question_id) references exam_published_questions (id),
  index idx_exam_published_options_question (published_question_id)
);

create table exam_published_answer_labels (
  id bigint primary key auto_increment,
  published_question_id bigint not null,
  answer_label varchar(8) not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_published_answer_labels_question foreign key (published_question_id) references exam_published_questions (id),
  index idx_exam_published_answer_labels_question (published_question_id)
);

create table exam_published_attachments (
  id bigint primary key auto_increment,
  published_question_id bigint not null,
  file_name varchar(255) not null,
  file_url varchar(1000) not null,
  media_type varchar(32) not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_published_attachments_question foreign key (published_question_id) references exam_published_questions (id),
  index idx_exam_published_attachments_question (published_question_id)
);

create table exam_departments (
  exam_id bigint not null,
  department_id bigint not null,
  created_at datetime not null default current_timestamp,
  primary key (exam_id, department_id),
  constraint fk_exam_departments_exam foreign key (exam_id) references exams (id),
  constraint fk_exam_departments_department foreign key (department_id) references departments (id)
);

create table exam_attempts (
  id bigint primary key auto_increment,
  exam_id bigint not null,
  user_id bigint not null,
  status varchar(20) not null,
  started_at datetime not null default current_timestamp,
  submitted_at datetime null,
  total_score decimal(7,2) not null default 0,
  obtained_score decimal(7,2) not null default 0,
  duration_seconds int not null default 0,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_exam_attempts_exam foreign key (exam_id) references exams (id),
  constraint fk_exam_attempts_user foreign key (user_id) references users (id),
  index idx_exam_attempts_exam_user (exam_id, user_id),
  index idx_exam_attempts_user (user_id),
  index idx_exam_attempts_status (status)
);

create table exam_attempt_questions (
  id bigint primary key auto_increment,
  attempt_id bigint not null,
  published_question_id bigint not null,
  source_question_id bigint not null,
  type varchar(32) not null,
  stem text not null,
  analysis text null,
  score decimal(6,2) not null,
  sort_order int not null,
  display_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_attempt_questions_attempt foreign key (attempt_id) references exam_attempts (id),
  index idx_exam_attempt_questions_attempt (attempt_id)
);

create table exam_attempt_options (
  id bigint primary key auto_increment,
  attempt_question_id bigint not null,
  option_label varchar(8) not null,
  content text not null,
  is_correct bit not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_attempt_options_question foreign key (attempt_question_id) references exam_attempt_questions (id),
  index idx_exam_attempt_options_question (attempt_question_id)
);

create table exam_attempt_answer_labels (
  id bigint primary key auto_increment,
  attempt_question_id bigint not null,
  answer_label varchar(8) not null,
  sort_order int not null default 0,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_attempt_answer_labels_question foreign key (attempt_question_id) references exam_attempt_questions (id),
  index idx_exam_attempt_answer_labels_question (attempt_question_id)
);

create table exam_attempt_attachments (
  id bigint primary key auto_increment,
  attempt_question_id bigint not null,
  file_name varchar(255) not null,
  file_url varchar(1000) not null,
  media_type varchar(32) not null,
  sort_order int not null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_attempt_attachments_question foreign key (attempt_question_id) references exam_attempt_questions (id),
  index idx_exam_attempt_attachments_question (attempt_question_id)
);

create table exam_answers (
  id bigint primary key auto_increment,
  attempt_id bigint not null,
  attempt_question_id bigint not null,
  selected_labels varchar(255) null,
  answer_text text null,
  is_correct bit null,
  score decimal(6,2) not null default 0,
  review_comment varchar(1000) null,
  reviewed_by bigint null,
  reviewed_at datetime null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_exam_answers_attempt foreign key (attempt_id) references exam_attempts (id),
  constraint fk_exam_answers_question foreign key (attempt_question_id) references exam_attempt_questions (id),
  constraint fk_exam_answers_reviewer foreign key (reviewed_by) references users (id),
  constraint uk_exam_answers_question unique (attempt_question_id),
  index idx_exam_answers_attempt (attempt_id)
);

create table exam_results (
  id bigint primary key auto_increment,
  attempt_id bigint not null,
  exam_id bigint not null,
  user_id bigint not null,
  total_score decimal(7,2) not null,
  obtained_score decimal(7,2) not null,
  objective_score decimal(7,2) not null default 0,
  subjective_score decimal(7,2) not null default 0,
  correct_count int not null,
  question_count int not null,
  grading_status varchar(20) not null default 'FINAL',
  submitted_at datetime not null,
  reviewed_at datetime null,
  created_at datetime not null default current_timestamp,
  constraint fk_exam_results_attempt foreign key (attempt_id) references exam_attempts (id),
  constraint fk_exam_results_exam foreign key (exam_id) references exams (id),
  constraint fk_exam_results_user foreign key (user_id) references users (id),
  constraint uk_exam_results_attempt unique (attempt_id),
  index idx_exam_results_exam (exam_id),
  index idx_exam_results_user (user_id)
);

insert into question_categories (id, name, description, sort_order)
values (1, '默认分类', '初始化题库分类', 10);

insert into question_banks (id, category_id, name, description, status)
values (1, 1, '四级样例题库', '简单单选、多选、写作样例题库', 'ACTIVE');

insert into questions (id, bank_id, type, stem, analysis, difficulty, status)
values
  (1, 1, 'SINGLE_CHOICE', 'Which word is closest in meaning to "essential"?', 'essential 表示必要的。', 'EASY', 'ACTIVE'),
  (2, 1, 'SINGLE_CHOICE', 'The lecture mainly discusses the importance of regular practice.', '听力材料中的核心观点是持续练习。', 'EASY', 'ACTIVE'),
  (3, 1, 'MULTIPLE_CHOICE', 'Which of the following are effective study habits?', '有效学习习惯包括复习、规划和练习。', 'EASY', 'ACTIVE'),
  (4, 1, 'WRITING', 'Write a short essay on online learning.', '写作题需要人工阅卷。', 'HARD', 'ACTIVE');

insert into question_options (question_id, option_label, content, is_correct, sort_order)
values
  (1, 'A', 'necessary', true, 10),
  (1, 'B', 'optional', false, 20),
  (1, 'C', 'ordinary', false, 30),
  (1, 'D', 'temporary', false, 40),
  (2, 'A', 'True', true, 10),
  (2, 'B', 'False', false, 20),
  (2, 'C', 'Not given', false, 30),
  (2, 'D', 'Unknown', false, 40),
  (3, 'A', 'Reviewing notes', true, 10),
  (3, 'B', 'Planning study time', true, 20),
  (3, 'C', 'Ignoring feedback', false, 30),
  (3, 'D', 'Practicing with past papers', true, 40);

insert into question_answer_labels (question_id, answer_label, sort_order)
values
  (1, 'A', 10),
  (2, 'A', 10),
  (3, 'A', 10),
  (3, 'B', 20),
  (3, 'D', 30);

insert into question_attachments (question_id, file_name, file_url, media_type, sort_order)
values (2, '2023-03-cet4-listening.mp3', '/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3', 'AUDIO', 10);

insert into exams (id, title, description, qualify_score, start_time, end_time, duration_minutes, time_limit, attempt_limit, exam_mode, display_mode, question_order_mode, open_type, status)
values (1, 'CET-4 四级考试平台演示', '简单题库演示考试', 20, '2026-01-01 00:00:00', '2099-12-31 23:59:59', 45, true, null, 'STRUCTURED', 'ALL', 'FIXED', 'PUBLIC', 'PUBLISHED');

insert into exams (id, title, description, qualify_score, start_time, end_time, duration_minutes, time_limit, attempt_limit, exam_mode, display_mode, question_order_mode, open_type, status)
values (2, '答题卡试卷演示', '试卷材料和答题卡分离的演示考试', 10, '2026-01-01 00:00:00', '2099-12-31 23:59:59', 45, true, null, 'ANSWER_SHEET', 'ALL', 'FIXED', 'PUBLIC', 'PUBLISHED');

insert into exam_published_questions (id, exam_id, source_question_id, bank_id, bank_name, type, stem, analysis, score, sort_order)
values
  (1, 1, 1, 1, '四级样例题库', 'SINGLE_CHOICE', 'Which word is closest in meaning to "essential"?', 'essential 表示必要的。', 5, 10),
  (2, 1, 2, 1, '四级样例题库', 'SINGLE_CHOICE', 'The lecture mainly discusses the importance of regular practice.', '听力材料中的核心观点是持续练习。', 5, 20),
  (3, 1, 3, 1, '四级样例题库', 'MULTIPLE_CHOICE', 'Which of the following are effective study habits?', '有效学习习惯包括复习、规划和练习。', 10, 30),
  (4, 1, 4, 1, '四级样例题库', 'WRITING', 'Write a short essay on online learning.', '写作题需要人工阅卷。', 15, 40);

insert into exam_published_options (published_question_id, option_label, content, is_correct, sort_order)
select question_id, option_label, content, is_correct, sort_order from question_options where question_id in (1, 2, 3);

insert into exam_published_answer_labels (published_question_id, answer_label, sort_order)
select question_id, answer_label, sort_order from question_answer_labels where question_id in (1, 2, 3);

insert into exam_published_attachments (published_question_id, file_name, file_url, media_type, sort_order)
select question_id, file_name, file_url, media_type, sort_order from question_attachments where question_id = 2;

insert into exam_material_groups (id, exam_id, title, description, sort_order)
values
  (1, 2, '听力材料', '播放音频后在下方答题卡填写答案。', 10),
  (2, 2, '阅读材料', '阅读材料可以是 PDF、图片、文件或外部链接。', 20);

insert into exam_material_files (id, group_id, exam_id, source_type, display_name, description, file_name, file_url, media_type, sort_order)
values
  (1, 1, 2, 'LOCAL_ASSET', '四级听力音频', '演示听力音频材料', '2023-03-cet4-listening.mp3', '/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3', 'AUDIO', 10),
  (2, 2, 2, 'EXTERNAL_LINK', '阅读材料链接', '演示外部材料链接', 'reading-material.html', 'https://example.com/reading-material.html', 'FILE', 10);

insert into exam_published_material_groups (id, exam_id, title, description, sort_order)
values
  (1, 2, '听力材料', '播放音频后在下方答题卡填写答案。', 10),
  (2, 2, '阅读材料', '阅读材料可以是 PDF、图片、文件或外部链接。', 20);

insert into exam_published_material_files (id, group_id, exam_id, source_type, display_name, description, file_name, file_url, media_type, sort_order)
values
  (1, 1, 2, 'LOCAL_ASSET', '四级听力音频', '演示听力音频材料', '2023-03-cet4-listening.mp3', '/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3', 'AUDIO', 10),
  (2, 2, 2, 'EXTERNAL_LINK', '阅读材料链接', '演示外部材料链接', 'reading-material.html', 'https://example.com/reading-material.html', 'FILE', 10);

insert into exam_answer_card_items (exam_id, question_no, answer_type, option_labels, correct_labels, score, sort_order)
values
  (2, 1, 'SINGLE_CHOICE', 'A,B,C,D', 'A', 5, 10),
  (2, 2, 'MULTIPLE_CHOICE', 'A,B,C,D', 'A,C', 5, 20),
  (2, 3, 'WRITING', '', '', 10, 30);

insert into exam_published_questions (id, exam_id, source_question_id, bank_id, bank_name, type, stem, analysis, score, sort_order)
values
  (5, 2, -1, 0, '答题卡', 'SINGLE_CHOICE', '第 1 题', null, 5, 10),
  (6, 2, -2, 0, '答题卡', 'MULTIPLE_CHOICE', '第 2 题', null, 5, 20),
  (7, 2, -3, 0, '答题卡', 'WRITING', '第 3 题', null, 10, 30);

insert into exam_published_options (published_question_id, option_label, content, is_correct, sort_order)
values
  (5, 'A', 'A', true, 10),
  (5, 'B', 'B', false, 20),
  (5, 'C', 'C', false, 30),
  (5, 'D', 'D', false, 40),
  (6, 'A', 'A', true, 10),
  (6, 'B', 'B', false, 20),
  (6, 'C', 'C', true, 30),
  (6, 'D', 'D', false, 40);

insert into exam_published_answer_labels (published_question_id, answer_label, sort_order)
values
  (5, 'A', 10),
  (6, 'A', 10),
  (6, 'C', 20);

insert into menus (id, code, title, path, parent_id, sort_order, icon)
values
  (6, 'question-banks', '题库管理', '/exam/repo', null, 60, 'Collection'),
  (7, 'exams', '考试管理', '/exam/manage', null, 70, 'Timer');

insert into role_menus (role_id, menu_id)
select 1, id from menus where id in (6, 7);
