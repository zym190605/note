-- =============================================================================
-- 一鸣笔记记录平台 · 一期数据库结构设计 (MySQL 8.x, utf8mb4)
-- 对齐：SRS + docs/openapi.yaml
-- =============================================================================
-- 约定：
--   • 主键均为 BIGINT UNSIGNED 自增，对外 API 以十进制字符串输出即可。
--   • 时间与 openapi 一致使用 ISO8601，库内 DATETIME(3) 毫秒精度。
--   • 用户数据隔离：所有业务表带 user_id 或通过 owner 链路校验。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. 用户与认证
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS `refresh_tokens`;
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号，唯一登录账号',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码摘要，禁止明文',
  `nickname` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '昵称/展示名',
  `avatar_url` VARCHAR(512) NULL COMMENT '头像 URL',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | DISABLED',
  `role` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER | ADMIN，后台管理员',
  `storage_quota_bytes` BIGINT UNSIGNED NOT NULL DEFAULT 5368709120 COMMENT '个人存储配额（可被注册时默认值覆盖），单位字节',
  `storage_used_bytes` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已通过附件累计的占用，上传/删除时维护',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `ui_theme` VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT 'LIGHT | DARK | SYSTEM（前端深浅色偏好，可 PATCH /users/me）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_phone` (`phone`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='注册用户';

CREATE TABLE `refresh_tokens` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `jti` CHAR(36) NOT NULL COMMENT '令牌唯一编号，可作吊销键',
  `token_hash` CHAR(64) NOT NULL COMMENT 'refresh 原文 SHA-256 hex，仅存摘要',
  `expires_at` DATETIME(3) NOT NULL,
  `remember_me` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为 7 天长会话',
  `revoked_at` DATETIME(3) NULL COMMENT '登出吊销时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_jti` (`jti`),
  KEY `idx_refresh_user_expires` (`user_id`, `expires_at`),
  CONSTRAINT `fk_refresh_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Refresh Token';


-- -----------------------------------------------------------------------------
-- 2. 笔记本（含默认「我的笔记」）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS `notebooks`;

CREATE TABLE `notebooks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '默认笔记本：不可改名、不可删',
  `note_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '名下未删除且未进回收站笔记数，业务层维护',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '左侧排序，可选',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_notebooks_user_updated` (`user_id`, `updated_at`),
  KEY `idx_notebooks_user_default` (`user_id`, `is_default`),
  CONSTRAINT `fk_notebooks_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记本';


-- -----------------------------------------------------------------------------
-- 3. 笔记（正文富文本 HTML + 乐观锁 + 回收站软删）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS `note_shares`;
DROP TABLE IF EXISTS `notes`;

CREATE TABLE `notes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `notebook_id` BIGINT UNSIGNED NOT NULL,
  `title` VARCHAR(500) NOT NULL DEFAULT '无标题',
  `content_html` MEDIUMTEXT NOT NULL COMMENT '富文本 HTML',
  `preview` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '列表纯文本摘要',
  `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁，每次成功保存 +1',
  `favored` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收藏',
  `deleted_at` DATETIME(3) NULL COMMENT '非空表示在回收站；配合定时任务按天数物理删除',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_notes_user_notebook_updated` (`user_id`, `notebook_id`, `updated_at`),
  KEY `idx_notes_user_favored_updated` (`user_id`, `favored`, `updated_at`),
  KEY `idx_notes_trash_expire` (`user_id`, `deleted_at`),
  FULLTEXT KEY `ft_notes_title_preview_content` (`title`, `preview`, `content_html`) COMMENT '全文检索一期可用 MySQL FULLTEXT；数据量大时再迁 ES',
  CONSTRAINT `fk_notes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_notes_notebook` FOREIGN KEY (`notebook_id`) REFERENCES `notebooks` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记';
-- 删除笔记本前（应用层）：① 迁移模式 — 将笔记迁入目标本后删空笔记本；② 连带删除 — 先将笔记 deleted_at 置位并把 notebook_id 改到默认笔记本，再物理删笔记本，否则会违反 RESTRICT。

CREATE TABLE `note_shares` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `note_id` BIGINT UNSIGNED NOT NULL COMMENT '每笔记最多一条有效分享（唯一约束）',
  `token` VARCHAR(64) NOT NULL COMMENT 'URL 公开访问的无鉴权令牌',
  `expires_at` DATETIME(3) NULL COMMENT '为空表示不过期',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_shares_note` (`note_id`),
  UNIQUE KEY `uk_note_shares_token` (`token`),
  KEY `idx_note_shares_user` (`user_id`),
  CONSTRAINT `fk_share_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_share_note` FOREIGN KEY (`note_id`) REFERENCES `notes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记公开分享链接';


-- -----------------------------------------------------------------------------
-- 4. 标签与笔记-标签多对多
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS `note_tags`;
DROP TABLE IF EXISTS `tags`;

CREATE TABLE `tags` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `color` CHAR(7) NULL COMMENT '#RRGGBB，可选',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tags_user_name` (`user_id`, `name`),
  CONSTRAINT `fk_tags_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签';

CREATE TABLE `note_tags` (
  `note_id` BIGINT UNSIGNED NOT NULL,
  `tag_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`note_id`, `tag_id`),
  KEY `idx_note_tags_tag` (`tag_id`),
  CONSTRAINT `fk_nt_note` FOREIGN KEY (`note_id`) REFERENCES `notes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_nt_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记-标签';


-- -----------------------------------------------------------------------------
-- 5. 图片/附件（占用空间、类型与大小限制在应用层按 system_settings 校验）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS `user_files`;

CREATE TABLE `user_files` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `note_id` BIGINT UNSIGNED NULL COMMENT '首次插入笔记时关联，便于清理孤儿文件',
  `storage_key` VARCHAR(512) NOT NULL COMMENT '对象存储或本地路径键',
  `public_url` VARCHAR(1024) NOT NULL COMMENT '前端富文本可引用的 URL',
  `mime_type` VARCHAR(128) NOT NULL,
  `size_bytes` BIGINT UNSIGNED NOT NULL,
  `width` INT UNSIGNED NULL,
  `height` INT UNSIGNED NULL,
  `sha256` CHAR(64) NULL COMMENT '可选去重',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_user_files_user` (`user_id`),
  KEY `idx_user_files_note` (`note_id`),
  CONSTRAINT `fk_files_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_files_note` FOREIGN KEY (`note_id`) REFERENCES `notes` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户上传文件（一期以图片为主）';


-- -----------------------------------------------------------------------------
-- 6. 系统配置（后台管理单表行，id 固定为 1）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS `system_settings`;

CREATE TABLE `system_settings` (
  `id` TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `default_user_quota_bytes` BIGINT UNSIGNED NOT NULL DEFAULT 5368709120 COMMENT '新用户默认存储配额',
  `max_image_size_bytes` INT UNSIGNED NOT NULL DEFAULT 10485760 COMMENT '单图上限 10MB',
  `allowed_image_mime_types` JSON NOT NULL COMMENT '如 ["image/jpeg","image/png","image/gif"]',
  `trash_retention_days` INT UNSIGNED NOT NULL DEFAULT 90,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_system_settings_singleton` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局系统配置';

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 初始化数据
-- -----------------------------------------------------------------------------

INSERT INTO `system_settings` (
  `id`,
  `default_user_quota_bytes`,
  `max_image_size_bytes`,
  `allowed_image_mime_types`,
  `trash_retention_days`
) VALUES (
  1,
  5368709120,
  10485760,
  '["image/jpeg", "image/png", "image/gif"]',
  90
);

-- 说明：注册成功后由应用层插入 users + 默认笔记本（name='我的笔记', is_default=1），勿手工依赖本脚本种子用户。
