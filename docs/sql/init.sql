-- =============================================
-- AIHub 数据库初始化脚本
-- 包含：用户、Token、AI任务、支付、RBAC、租户、工作流等
-- =============================================

CREATE DATABASE IF NOT EXISTS aihub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE aihub;

-- ==================== 用户体系 ====================

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像URL',
    mobile VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    password VARCHAR(255) COMMENT '密码',
    vip_level INT DEFAULT 0 COMMENT 'VIP等级: 0免费 1VIP 2SVIP 9企业',
    token_balance BIGINT DEFAULT 0 COMMENT 'Token余额',
    status TINYINT DEFAULT 1 COMMENT '状态: 1正常 0禁用',
    last_login_time DATETIME COMMENT '最后登录时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_mobile (mobile),
    INDEX idx_email (email),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户登录日志
CREATE TABLE IF NOT EXISTS `user_login_log` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    ip VARCHAR(100) COMMENT '登录IP',
    device VARCHAR(255) COMMENT '设备信息',
    login_type VARCHAR(50) COMMENT '登录方式: mobile/email/wechat',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录日志';

-- VIP 会员表
CREATE TABLE IF NOT EXISTS `user_vip` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    vip_level INT COMMENT 'VIP等级',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP会员表';

-- ==================== Token 体系 ====================

-- Token 流水表
CREATE TABLE IF NOT EXISTS `token_log` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    business_type VARCHAR(50) COMMENT '业务类型: chat/image/video/voice/ppt',
    token_change INT COMMENT 'Token变化量(正数为充值,负数为消费)',
    remain_token INT COMMENT '剩余Token',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_business_type (business_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token流水表';

-- Token 账单表
CREATE TABLE IF NOT EXISTS `token_bill` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    user_id BIGINT COMMENT '用户ID',
    model_name VARCHAR(50) COMMENT '模型名称',
    token_used INT COMMENT '消耗Token数',
    cost DECIMAL(10,4) COMMENT '成本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token账单表';

-- ==================== 支付体系 ====================

-- 支付订单表
CREATE TABLE IF NOT EXISTS `pay_order` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    order_no VARCHAR(100) COMMENT '订单号',
    amount DECIMAL(10,2) COMMENT '金额',
    pay_status TINYINT DEFAULT 0 COMMENT '支付状态: 0待支付 1已支付 2已退款',
    pay_type VARCHAR(50) COMMENT '支付方式: wechat/alipay',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_order_no (order_no),
    INDEX idx_pay_status (pay_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- ==================== AI 聊天 ====================

-- 聊天会话表
CREATE TABLE IF NOT EXISTS `ai_chat_session` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    title VARCHAR(255) COMMENT '会话标题',
    model VARCHAR(50) COMMENT '模型: gpt-4o, claude-3, deepseek',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天会话表';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS `ai_chat_message` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT COMMENT '会话ID',
    role VARCHAR(20) COMMENT '角色: user/assistant/system',
    content LONGTEXT COMMENT '消息内容',
    token_cost INT DEFAULT 0 COMMENT '消耗Token',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息表';

-- AI 聊天记录表（计费用）
CREATE TABLE IF NOT EXISTS `ai_chat_record` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    session_id BIGINT COMMENT '会话ID',
    model_name VARCHAR(50) COMMENT '模型名称',
    prompt LONGTEXT COMMENT '用户输入',
    completion LONGTEXT COMMENT 'AI输出',
    prompt_tokens INT DEFAULT 0 COMMENT '输入Token数',
    completion_tokens INT DEFAULT 0 COMMENT '输出Token数',
    cost DECIMAL(10,4) COMMENT '成本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天记录表';

-- ==================== AI 图片 ====================

-- AI 图片任务表
CREATE TABLE IF NOT EXISTS `ai_image_task` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    prompt TEXT COMMENT '正向提示词',
    negative_prompt TEXT COMMENT '反向提示词',
    model VARCHAR(50) COMMENT '模型: stable-diffusion, flux, midjourney',
    width INT DEFAULT 1024 COMMENT '宽度',
    height INT DEFAULT 1024 COMMENT '高度',
    status TINYINT DEFAULT 0 COMMENT '状态: 0等待 1处理中 2成功 3失败',
    result_url VARCHAR(500) COMMENT '结果图片URL',
    token_cost INT DEFAULT 0 COMMENT '消耗Token',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI图片任务表';

-- ==================== AI 视频 ====================

-- AI 视频任务表
CREATE TABLE IF NOT EXISTS `ai_video_task` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    title VARCHAR(255) COMMENT '任务标题',
    prompt TEXT COMMENT '提示词',
    model VARCHAR(50) COMMENT '模型: runway, kling, hailuo',
    duration INT DEFAULT 5 COMMENT '时长(秒)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0等待 1处理中 2成功 3失败',
    result_url VARCHAR(500) COMMENT '结果视频URL',
    token_cost INT DEFAULT 0 COMMENT '消耗Token',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI视频任务表';

-- ==================== AI 配音 ====================

-- AI 配音任务表
CREATE TABLE IF NOT EXISTS `ai_voice_task` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    text_content LONGTEXT COMMENT '文本内容',
    voice_type VARCHAR(50) COMMENT '音色类型',
    result_url VARCHAR(500) COMMENT '结果音频URL',
    token_cost INT DEFAULT 0 COMMENT '消耗Token',
    status TINYINT DEFAULT 0 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI配音任务表';

-- ==================== 文件管理 ====================

-- 文件记录表
CREATE TABLE IF NOT EXISTS `file_record` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    file_name VARCHAR(255) COMMENT '文件原名',
    file_url VARCHAR(500) COMMENT '存储URL',
    file_size BIGINT COMMENT '文件大小(字节)',
    file_type VARCHAR(50) COMMENT '文件类型',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件记录表';

-- ==================== RBAC 权限体系 ====================

-- 系统用户表（后台管理）
CREATE TABLE IF NOT EXISTS `sys_user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像',
    email VARCHAR(100) COMMENT '邮箱',
    mobile VARCHAR(20) COMMENT '手机号',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(100) NOT NULL COMMENT '角色编码',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS `sys_permission` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0 COMMENT '父权限ID',
    permission_name VARCHAR(50) COMMENT '权限名称',
    permission_code VARCHAR(100) COMMENT '权限编码',
    permission_type VARCHAR(20) COMMENT '类型: menu/button/api',
    path VARCHAR(255) COMMENT '路由路径',
    icon VARCHAR(100) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ==================== 多租户 ====================

-- 租户表
CREATE TABLE IF NOT EXISTS `tenant` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_name VARCHAR(100) COMMENT '租户名称',
    tenant_code VARCHAR(100) COMMENT '租户编码',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_mobile VARCHAR(20) COMMENT '联系电话',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- ==================== API Key ====================

-- API Key 表
CREATE TABLE IF NOT EXISTS `api_key` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT COMMENT '租户ID',
    app_name VARCHAR(100) COMMENT '应用名称',
    api_key VARCHAR(255) COMMENT 'API Key',
    api_secret VARCHAR(255) COMMENT 'API Secret',
    qps_limit INT DEFAULT 10 COMMENT 'QPS限制',
    daily_limit BIGINT DEFAULT 10000 COMMENT '每日调用限制',
    status TINYINT DEFAULT 1 COMMENT '状态: 1正常 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_api_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key表';

-- ==================== 工作流 ====================

-- 工作流表
CREATE TABLE IF NOT EXISTS `workflow` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    workflow_name VARCHAR(100) COMMENT '工作流名称',
    workflow_json LONGTEXT COMMENT '工作流JSON定义',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表';

-- ==================== Agent ====================

-- Agent 表
CREATE TABLE IF NOT EXISTS `agent` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    agent_name VARCHAR(100) COMMENT 'Agent名称',
    agent_prompt LONGTEXT COMMENT 'Agent系统提示词',
    workflow_id BIGINT COMMENT '关联工作流ID',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent表';

-- ==================== RAG 知识库 ====================

-- RAG 文档表
CREATE TABLE IF NOT EXISTS `rag_document` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    document_name VARCHAR(255) COMMENT '文档名称',
    file_url VARCHAR(500) COMMENT '文件URL',
    vector_status TINYINT DEFAULT 0 COMMENT '向量化状态: 0未处理 1处理中 2已完成',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG文档表';

-- RAG 分片表
CREATE TABLE IF NOT EXISTS `rag_chunk` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT COMMENT '文档ID',
    chunk_text LONGTEXT COMMENT '分片文本',
    embedding_status TINYINT DEFAULT 0 COMMENT '向量化状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG分片表';

-- ==================== 初始化数据 ====================

-- 初始化角色
INSERT INTO sys_role(role_name, role_code) VALUES ('超级管理员', 'SUPER_ADMIN');
INSERT INTO sys_role(role_name, role_code) VALUES ('运营人员', 'OPERATOR');
INSERT INTO sys_role(role_name, role_code) VALUES ('审核人员', 'AUDITOR');

-- 初始化管理员用户 (密码: admin123, BCrypt加密)
INSERT INTO sys_user(username, password, nickname, status)
VALUES ('admin', '$2a$10$4imQ6Dksujiq.SbX3Codt./QYtOEzo0SV.tG9rY6C9PVo55IyVlEe', '超级管理员', 1);

-- 初始化默认租户
INSERT INTO tenant(tenant_name, tenant_code) VALUES ('AIHub', 'default');
