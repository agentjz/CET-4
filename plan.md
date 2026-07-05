# 考试平台生产级核心能力补齐计划

## 需求

owner 追加指出：当前 plan 只盯考试治理还不够，平台连自助注册、邮箱验证码、找回密码、邮件环境配置和完整用户入口体验都没有，这不符合生产级考试平台。需要重新 research 和规划，先把身份入口、用户体验、考试治理一起看清楚，再开干。

本计划是当前任务的单文件执行合同。当前轮次先修正计划，不继续沿旧计划直接实现。

## 当前事实

已确认事实来自 `AGENTS.md`、三个项目 skill、`spec.md`、`README.md`、后端认证代码、配置文件、前端登录页、数据库脚本、测试文件和全局搜索。

- 当前认证后端只有：
  - `POST /api/auth/login`
  - `GET /api/auth/me`
  - `POST /api/auth/logout`
  - `POST /api/auth/change-password`
- 当前没有自助注册、邮箱验证码、注册确认、忘记密码、重置密码、邮箱绑定、邮箱唯一性、注册开关、SMTP 配置和验证码限流。
- 当前 `application.yml` 只有数据库、Redis、Flyway、JWT 等配置，没有 `spring.mail` 或平台邮件配置。
- 当前 `users` 表包含用户名、展示名、密码哈希、状态、首次改密标识、部门，没有 email、emailVerified、注册来源、注册审批状态等字段。
- 当前用户主要通过管理员新建或 Excel 导入进入系统；新建/导入默认密码 `123456`，首次登录必须改密。
- 当前登录页是演示心智：默认填入 `admin/password`，文案强调“进入演示工作台”，没有注册、忘记密码、邮箱验证、注册成功、重置密码、邮件配置异常等用户路径。
- 当前平台已经具备考试主骨架：用户、角色、权限、部门、题库、试题、考试、发布快照、作答快照、自动评分、写作阅卷、成绩详情、附件和 demo。
- 当前答题卡试卷模式已经完成材料分组、材料文件、答题卡条目、发布材料快照、作答快照、提交、自动评分和写作人工阅卷。
- 当前仍缺考试治理能力：考生名册/考试分配、个人 allowance、attempt 重开/补考、统一业务审计、成绩发布策略、基础报表、文件资产 registry、安全事件。
- 当前多数管理接口仍以 `system:admin` 保护，权限数据存在但接口粒度偏粗。
- 当前真实浏览器和后端测试覆盖主考试链路较强，但没有覆盖注册、邮箱验证、找回密码、注册后参加考试等入口链路。

## 外部参考结论

成熟考试/学习平台常见入口不是只有管理员发账号，而是至少提供两种模式：

- 机构闭环模式：管理员导入/创建账号，分配部门、角色和考试。
- 自助注册模式：用户用邮箱注册，通过验证码或邮件链接验证身份，再进入考生角色或等待审批。

生产级身份入口还需要：

- 注册开关和注册策略，避免公开部署时被垃圾账号冲击。
- 邮箱验证码或验证链接，验证码有过期时间、重发间隔、错误次数限制和审计。
- 忘记密码流程，避免用户只能找管理员重置。
- 邮件发送配置和配置自检，支持 QQ 邮箱、企业邮箱、SMTP 服务商等，但不能把 QQ 邮箱写死成唯一模型。
- 对注册、登录失败、验证码发送、密码重置等敏感动作做限流、审计和统一错误提示。

结论：kaoshi 要成为可长期维护的考试平台，身份入口必须作为第一优先级补齐。考试治理是第二优先级，不能跳过用户入口直接做后台治理。

## 核心能力定义

生产级 kaoshi 的核心应分为三层：

1. 身份入口与账号生命周期：注册、邮箱验证、登录、首次改密、忘记密码、重置密码、账号启停、角色、部门、注册策略、邮件配置。
2. 考试生命周期：题库、试题、试卷、材料、答题卡、发布快照、作答快照、提交锁定、评分、阅卷、成绩和复盘。
3. 平台治理：考生范围、考试分配、个人授权、审计、成绩发布策略、报表、安全事件、文件资产、通知和外部集成。

## 用户心智问题

### 未登录用户

- 现在用户只能看到登录，不知道能不能注册、忘记密码怎么办、邮箱是否可用、平台是演示还是可正式使用。
- 登录页默认账号密码适合 demo，但正式部署会让用户误解系统是玩具。
- 注册成功后的下一步不清楚：是直接进入考试中心、等待管理员审批，还是需要邮箱验证。

### 考生

- 考生应能用邮箱注册或被管理员导入。
- 考生注册后应明确知道账号状态：未验证邮箱、待审批、已启用、被禁用。
- 考试中心应告诉考生能参加哪些考试、为什么不能参加、剩余次数、成绩是否发布。
- 找回密码应通过邮箱完成，而不是只能联系管理员。

### 管理员

- 管理员需要配置是否允许自助注册、默认注册角色、是否需要审批、允许邮箱域名、邮件服务状态。
- 管理员需要看到新注册用户，审核、分配部门、调整角色。
- 管理员需要审计注册、验证码、登录失败、密码重置和考试关键动作。

### 部署者

- 部署者需要清楚设置 SMTP：例如 QQ 邮箱使用 SMTP host、端口、账号、授权码；企业邮箱或云邮件服务用同一套配置。
- 如果未配置邮件服务，系统不能假装能发验证码，应在页面和管理端给出明确状态。

## 生产级验收保护点

- 不把 QQ 邮箱写死；QQ 邮箱只是 SMTP 配置示例。
- 不把自助注册作为唯一入口；保留管理员创建/导入用户。
- 注册必须经过邮箱验证或管理员审批策略，不能默认制造可用垃圾账号。
- 验证码和重置 token 必须过期、限流、单次使用、不可明文长期保存。
- 注册、验证码、重置密码、登录失败必须有审计事件。
- 邮件未配置时，注册页和管理端必须给出可理解状态；测试环境可以使用内存/日志邮件发送器。
- 学生端成绩详情必须受成绩发布策略控制，不能只靠前端隐藏。
- 发布快照和作答快照仍是考试事实边界，身份入口改造不能破坏考试链路。
- 新增能力必须同步后端测试、前端类型/单测、真实浏览器路径、demo 同源体验和文档事实。
- 文件职责按变化原因拆分，不把注册、邮件、审计、考试治理塞进 `AuthService` 或 `ExamService`。

## 目标

第一阶段先补齐生产级入口：

1. 建立邮箱注册、邮箱验证码、邮箱验证状态和注册策略。
2. 建立忘记密码、邮件验证码/重置 token、重置密码流程。
3. 建立邮件服务配置、QQ 邮箱/SMTP 配置说明和邮件发送自检。
4. 重做登录页信息架构：登录、注册、忘记密码、验证邮箱、演示入口清晰分离。
5. 建立身份审计：注册、发送验证码、验证邮箱、登录失败、密码重置、管理员审核。

第二阶段补齐考试治理事实基础：

1. 考生名册/考试分配。
2. 个人 allowance：额外时间、额外次数、补考、重开 attempt。
3. 统一业务审计事件。
4. 成绩发布策略。
5. 基础考试报表。

第三阶段再补平台化能力：

1. 文件资产 registry。
2. 安全策略和监考事件。
3. 阅卷 rubric、阅卷任务、复核。
4. 通知中心和外部集成边界。

## 不做范围

- 不做手机号短信注册。
- 不接 OAuth/微信/QQ 登录，除非后续单独规划。
- 不把邮箱服务商绑定成 QQ 邮箱；只提供 SMTP 通用配置和 QQ 邮箱示例。
- 不做复杂租户系统。
- 不恢复复杂题组结构。
- 不新增大量题型。
- 不立刻做全量 BI、第三方监考、SSO、LTI、xAPI、QTI。

## 数据结构设计

### 用户账号扩展

扩展 `users` 当前事实：

- `email`
- `email_verified`
- `registration_source`：`ADMIN_CREATED`、`IMPORT`、`SELF_REGISTERED`
- `approval_status`：`APPROVED`、`PENDING`、`REJECTED`
- `registered_at`
- `last_login_at`

约束：

- email 唯一，允许历史空值。
- 自助注册账号必须有 email。
- 未验证或未审批账号不能进入需要登录的业务页。

### 邮箱验证码

新增 `email_verification_codes`：

- `id`
- `email`
- `purpose`：`REGISTER`、`RESET_PASSWORD`、`BIND_EMAIL`
- `code_hash`
- `expires_at`
- `consumed_at`
- `send_count`
- `failed_attempt_count`
- `last_sent_at`
- `ip_address`
- `user_agent`
- `created_at`

规则：

- 验证码只展示一次，数据库只存 hash。
- 默认 10 分钟过期。
- 同一邮箱同一 purpose 有重发间隔。
- 连续错误后短时间锁定。

### 注册策略

新增 `auth_registration_settings` 或放入 `system_configs`：

- `self_registration_enabled`
- `email_verification_required`
- `admin_approval_required`
- `default_role_code`
- `default_department_id`
- `allowed_email_domains`
- `terms_text`

首期可以用 `system_configs` 保存 JSON 或键值，但要有类型化 DTO 和 service，不让前端直接编辑裸 JSON。

### 邮件配置

配置来源优先使用环境变量，不把敏感信息写数据库：

- `KAOSHI_MAIL_ENABLED`
- `KAOSHI_MAIL_HOST`
- `KAOSHI_MAIL_PORT`
- `KAOSHI_MAIL_USERNAME`
- `KAOSHI_MAIL_PASSWORD`
- `KAOSHI_MAIL_FROM`
- `KAOSHI_MAIL_PROTOCOL`
- `KAOSHI_MAIL_SSL_ENABLED`
- `KAOSHI_MAIL_STARTTLS_ENABLED`

管理端只读展示配置状态，并提供发送测试邮件接口，不回显密码。

### 审计事件

新增统一 `audit_events`：

- `id`
- `actor_user_id`
- `actor_username`
- `action`
- `resource_type`
- `resource_id`
- `resource_title`
- `ip_address`
- `user_agent`
- `payload_json`
- `created_at`

首批覆盖身份入口：

- `AUTH_REGISTER_REQUESTED`
- `AUTH_VERIFICATION_SENT`
- `AUTH_EMAIL_VERIFIED`
- `AUTH_LOGIN_FAILED`
- `AUTH_PASSWORD_RESET_REQUESTED`
- `AUTH_PASSWORD_RESET_COMPLETED`
- `AUTH_REGISTRATION_APPROVED`
- `AUTH_REGISTRATION_REJECTED`

后续再覆盖考试治理动作。

### 考试治理表

保留上一轮规划：

- `exam_participants`
- `exam_participant_allowances`
- `exam_attempt_events`
- `exam_result_policies`
- `file_assets`

但实施顺序排在身份入口之后。

## 后端接口设计

### 公开认证接口

- `POST /api/auth/register`
  - 入参：email、username、displayName、password、confirmPassword、verificationCode 或先不带 code 的两步注册。
  - 出参：注册状态、是否需要邮箱验证、是否需要管理员审批。
- `POST /api/auth/verification-codes`
  - 入参：email、purpose。
  - 行为：发送验证码，受注册策略和限流控制。
- `POST /api/auth/verify-email`
  - 入参：email、purpose、code。
- `POST /api/auth/password-reset-codes`
  - 入参：email。
- `POST /api/auth/reset-password`
  - 入参：email、code、newPassword、confirmPassword。

### 管理端身份配置

- `GET /api/admin/auth/registration-settings`
- `PUT /api/admin/auth/registration-settings`
- `GET /api/admin/auth/mail-status`
- `POST /api/admin/auth/test-mail`
- `GET /api/admin/auth/registration-requests`
- `POST /api/admin/auth/registration-requests/{userId}/approve`
- `POST /api/admin/auth/registration-requests/{userId}/reject`

### 考试治理接口

第二阶段实现：

- 考生分配、allowance、attempt 重开/重置、成绩发布策略、报表、审计查询。

## 前端信息架构

### 登录页

登录页改成清晰入口，而不是单一演示表单：

- 左侧：产品定位和当前部署模式。
- 右侧 tabs：
  - 登录。
  - 注册。
  - 找回密码。
- demo 模式可以保留“一键体验”或默认账号提示，但 real 模式不默认填入生产账号。
- 邮件未配置时，注册/找回密码页显示“当前部署未启用邮件服务，请联系管理员”，不让用户白填。

### 注册体验

注册流程：

1. 输入邮箱、用户名、姓名、密码。
2. 点击发送验证码。
3. 输入验证码并提交注册。
4. 根据策略进入：
   - 邮箱已验证且无需审批：注册成功，进入登录或自动登录。
   - 需要审批：显示等待管理员审核。
   - 邮件未配置：提示联系管理员。

### 找回密码体验

1. 输入邮箱。
2. 发送验证码。
3. 输入验证码和新密码。
4. 重置成功后回到登录。

### 管理端设置

新增或放入系统管理：

- 注册与邮件设置。
- 注册申请审核。
- 邮件状态自检。
- 审计日志。

首期可以新增一个「平台设置」入口，不把配置塞进用户管理页。

### 考试端 UX

身份入口补齐后继续优化：

- 考试中心显示资格状态、剩余次数、成绩发布状态。
- 准备页显示考试规则确认。
- 成绩详情按发布策略显示。

## 权限设计

新增权限建议：

- `system:settings`：注册策略和邮件状态。
- `system:audit`：审计日志。
- `system:users`：用户和注册审核。
- `exam:questions`：题库试题。
- `exam:manage`：考试管理和考生分配。
- `exam:review`：阅卷。
- `exam:report`：报表。
- `exam:take`：考试作答。
- `system:admin`：兜底全权限。

首期可以保持 `system:admin` 兼容测试入口，但新接口要按新权限设计，后续再细化已有接口。

## 后端模块职责设计

新增模块，不塞进现有大 service：

- `AuthRegistrationService`：注册策略、注册、邮箱验证。
- `PasswordResetService`：找回密码和重置密码。
- `MailService`：邮件发送、模板、测试邮件。
- `MailProperties`：SMTP 配置。
- `AuditEventService`：统一审计写入。
- `RegistrationSettingsService`：注册策略读写。
- `AdminRegistrationController`：管理端注册审核和设置。

现有 `AuthService` 只保留登录、当前用户、改密等已登录认证流程。

## Demo 设计

demo 必须同源展示入口心智：

- 登录页展示登录、注册、找回密码。
- demo 注册不发真实邮件，使用内存验证码或固定提示验证码。
- 注册后默认考生角色，能进入考试中心。
- demo 覆盖找回密码流程，不写 `localStorage`。
- demo 不展示真实 SMTP 密码。

## 测试计划

### 后端

- 注册关闭时拒绝自助注册。
- 邮件未配置时发送验证码返回明确错误。
- 发送注册验证码成功，验证码 hash 入库，过期时间正确。
- 验证码错误、过期、重复使用、重发间隔、错误次数限制。
- 注册成功创建用户，默认角色和审批状态符合策略。
- 未验证/待审批用户不能登录。
- 忘记密码验证码和重置密码成功，旧密码失效。
- 注册、验证码、重置密码、登录失败写审计。
- 管理员审核注册申请。

### 前端单元

- auth API client 覆盖注册、验证码、重置密码、邮件状态。
- 注册表单校验：邮箱、密码确认、验证码。
- 找回密码表单校验。
- demo auth adapter 覆盖注册和重置密码。

### 真实浏览器

- 登录页进入注册 tab。
- 邮箱验证码注册新考生。
- 新考生登录，首次进入考试中心。
- 忘记密码发送验证码并重置密码。
- 管理员查看注册申请并审核。
- 邮件未配置时注册页给出明确提示。

### 考试治理后续测试

保留上一轮测试计划：指定考生、allowance、attempt 重开、审计、成绩发布、报表、真实浏览器完整链路。

### 收口验证

- `python .\start_test.py`
- `python .\start_browser_test.py`
- 涉及 demo 时：
  - `cd frontend; npm.cmd run build:demo`
  - `cd frontend; npm.cmd run test:e2e:demo`

## 文件职责审查

当前认证相关文件职责判断：

- `AuthController.java`：当前只接登录、当前用户、登出、改密，可保留；注册和找回密码新增 controller，不继续塞。
- `AuthService.java`：当前负责登录、当前用户、改密；新增注册/重置密码会让变化原因混杂，必须拆新 service。
- `LoginView.vue`：当前是单一登录页；改成 auth landing 后如果继续承载登录、注册、找回密码三套表单，会变重，应拆为：
  - `LoginForm.vue`
  - `RegisterForm.vue`
  - `ForgotPasswordForm.vue`
  - `AuthShell.vue` 或由 `LoginView.vue` 组合。
- `auth-demo-adapter.ts`：新增注册和重置密码后要保持 adapter 简洁，验证码状态放 demo auth runtime/helper。
- `UserMapper` 和 `AdminUserService`：不要承载注册验证码和邮件逻辑；只负责用户数据读写和管理员用户管理。

考试相关职责延续上一轮结论：

- 后续新增 `ExamParticipantService`、`ExamAllowanceService`、`ExamResultPolicyService`、`ExamReportService`。
- 不继续把治理能力塞进 `ExamService`、`ExamsView.vue` 或 `useExamEditor.ts`。

## 实施 checklist

- [x] 重新 research 当前认证、配置、登录 UI 和搜索结果。
- [x] 确认缺少注册、邮箱验证码、忘记密码、SMTP 配置和入口 UX。
- [x] 重写计划，把身份入口列为第一优先级。
- [ ] 后端扩展 users 表和注册/验证码/审计表。
- [ ] 后端新增邮件配置、邮件发送、验证码、注册、重置密码和注册策略服务。
- [ ] 后端新增公开认证接口和管理端注册设置/审核接口。
- [ ] 前端拆分登录、注册、找回密码组件。
- [ ] 前端新增注册与邮件设置、注册申请审核入口。
- [ ] demo 支持注册和找回密码。
- [ ] 后端、前端、真实浏览器测试覆盖身份入口。
- [ ] 第二阶段实现考生分配、allowance、审计、成绩策略和报表。
- [ ] 同步 `spec.md`、`README.md` 当前事实。
- [ ] 运行收口验证并记录结果。

## 收口标准

身份入口第一阶段完成标准：

- 用户可以用邮箱注册账号，并通过验证码验证邮箱。
- 系统支持关闭自助注册、开启审批、配置默认角色和默认部门。
- 用户可以通过邮箱验证码找回密码。
- 邮件服务支持通用 SMTP 配置，QQ 邮箱可通过环境变量配置，不写死。
- 邮件未配置时页面和接口都给出明确状态。
- 注册、验证码、重置密码、登录失败有审计事件。
- 登录页不再只有演示账号入口，真实使用路径清楚。
- demo 能体验注册和找回密码但不发真实邮件。
- 后端和真实浏览器测试通过。

全局生产级最终标准：

- 身份入口、考试治理、成绩发布、报表、安全审计、文件资产逐步形成可维护模块。
- 现有结构化试卷、答题卡试卷、发布快照、作答快照、自动评分、人工阅卷不退化。
- 文件职责按变化原因维护。
- `python .\start_test.py` 通过。
- `python .\start_browser_test.py` 通过。
- demo 相关命令在涉及 demo 改动时通过。
- `spec.md` 和 `README.md` 只记录已经实现并验证的当前事实。
- commit/push 前必须得到 owner 明确确认。
