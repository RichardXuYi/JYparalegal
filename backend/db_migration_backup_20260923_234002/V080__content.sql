-- ============================================================
-- V080__content.sql
-- 功能组【内容（资讯/站点内容）】版本号区间：V080–V089
-- 表：news_categories / news_articles / news_comments /
--     news_likes / site_contents
--
-- 重构说明：
--   - 添加外键约束（articles->categories, comments->articles/users,
--     likes->users/articles）
--   - 补建外键列索引
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
--   - 种子数据版本号对齐 v1.4.58（原 V121/V122 的错误 UPDATE 已消除）
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 资讯分类
CREATE TABLE IF NOT EXISTS `news_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `parent_id` bigint,
  `sort_order` int DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_news_categories_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯分类表';

-- 资讯文章
CREATE TABLE IF NOT EXISTS `news_articles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `category_id` bigint,
  `author_id` bigint,
  `cover_image` varchar(255),
  `summary` varchar(500),
  `content` LONGTEXT,
  `status` int DEFAULT 0,
  `view_count` int DEFAULT 0,
  `like_count` int DEFAULT 0,
  `share_count` int DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `is_top` tinyint(1) NOT NULL DEFAULT 0,
  `publish_time` datetime NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_news_articles_category_id` (`category_id`),
  KEY `idx_news_articles_author_id` (`author_id`),
  CONSTRAINT `fk_news_articles_category_id` FOREIGN KEY (`category_id`) REFERENCES `news_categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯文章表';

-- 资讯评论
CREATE TABLE IF NOT EXISTS `news_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `news_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `content` text NOT NULL,
  `parent_id` bigint,
  `status` int NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_news_comments_news_id` (`news_id`),
  KEY `idx_news_comments_user_id` (`user_id`),
  KEY `idx_news_comments_parent_id` (`parent_id`),
  CONSTRAINT `fk_news_comments_news_id` FOREIGN KEY (`news_id`) REFERENCES `news_articles` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_news_comments_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯评论表';

-- 资讯点赞（同一用户+文章唯一）
CREATE TABLE IF NOT EXISTS `news_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `news_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_news_likes_user_news` (`user_id`, `news_id`),
  CONSTRAINT `fk_news_likes_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_news_likes_news_id` FOREIGN KEY (`news_id`) REFERENCES `news_articles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯点赞表';

-- 品牌站点内容（official-web 数据源）
CREATE TABLE IF NOT EXISTS `site_contents` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `section` VARCHAR(50) NOT NULL COMMENT 'section: homepage, about, culture, downloads, site_settings',
  `content_key` VARCHAR(100) NOT NULL,
  `content_value` JSON NOT NULL,
  `sort_order` INT DEFAULT 0,
  `status` INT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_site_contents_section_key` (`section`, `content_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌站点内容表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 站点内容种子数据（版本号对齐 v1.4.58，INSERT IGNORE 安全重放）
-- ============================================================

INSERT IGNORE INTO `site_contents` (`section`, `content_key`, `content_value`, `sort_order`) VALUES
('homepage', 'hero_title', '{"value": "金元·数智员工平台"}', 1),
('homepage', 'hero_subtitle', '{"value": "从数字 Agent 出发，向数字人演进--为每个组织雇佣可靠的 AI 数智员工"}', 2),
('homepage', 'stats', '{"items": [{"label": "接入模型", "value": "10+", "suffix": ""}, {"label": "可插拔技能", "value": "100+", "suffix": ""}, {"label": "服务可用性", "value": "99.9", "suffix": "%"}, {"label": "进驻渠道", "value": "6+", "suffix": ""}]}', 3),
('homepage', 'business_cards', '{"items": [{"icon": "Brain", "title": "多模型编排", "desc": "DeepSeek / GLM / Qwen 多模型调度，让数智员工始终使用最合适的大脑"}, {"icon": "Shield", "title": "知识记忆", "desc": "Qdrant 向量知识库 + RAG 增强检索，沉淀专属组织记忆"}, {"icon": "TrendingUp", "title": "技能生态", "desc": "可插拔 Skill 技能库，数智员工按需学习新能力"}, {"icon": "GraduationCap", "title": "多端进驻", "desc": "钉钉 / 飞书 / 企业微信等渠道一键进驻，融入真实工作流"}]}', 4);

INSERT IGNORE INTO `site_contents` (`section`, `content_key`, `content_value`, `sort_order`) VALUES
('about', 'intro', '{"title": "关于金元·数智员工", "description": "金元·数智员工平台致力于把每一个 AI Agent 打造成可被雇佣的数智员工：有工作台、有技能、有记忆、能进驻，并朝着拟人形象与多模态交互的数字人方向持续演进。"}', 1),
('about', 'timeline', '{"items": [{"year": "阶段一", "title": "数字 Agent", "desc": "多模型编排、Skill 技能库与 RAG 知识记忆"}, {"year": "阶段二", "title": "数智员工", "desc": "多渠道进驻、平台化运营、会员与内容生态"}, {"year": "阶段三", "title": "数字人", "desc": "拟人形象与语音、多模态交互（演进方向）"}]}', 2),
('about', 'business_lines', '{"items": [{"no": "1.0", "title": "智能体工作台", "desc": "GrandPoem Studio 桌面端与 Web 端，OpenClaw 网关统一调度多模型能力。"}, {"no": "2.0", "title": "技能与记忆", "desc": "可插拔 Skill 技能库跨端同步；Qdrant 向量知识库沉淀组织专属记忆。"}, {"no": "3.0", "title": "渠道进驻", "desc": "钉钉、飞书、企业微信等渠道一键进驻，数智员工融入真实协作流。"}, {"no": "4.0", "title": "平台运营", "desc": "资讯、活动、会员与商城体系，支撑数智员工服务的持续运营。"}]}', 3),
('about', 'team', '{"items": []}', 4),
('about', 'partners', '{"items": []}', 5);

INSERT IGNORE INTO `site_contents` (`section`, `content_key`, `content_value`, `sort_order`) VALUES
('culture', 'mission', '{"value": "让每个组织都能雇佣可靠的数智员工"}', 1),
('culture', 'vision', '{"value": "从数字 Agent 到数字人，成为最值得信赖的智能体平台"}', 2),
('culture', 'values', '{"items": [{"icon": "Lightbulb", "title": "演进驱动", "desc": "沿着 Agent -> 数智员工 -> 数字人的路线持续演进"}, {"icon": "Users", "title": "用户至上", "desc": "深入理解用户需求，提供超越预期的服务体验"}, {"icon": "Shield", "title": "可信可靠", "desc": "安全审计与权限体系内建，让 AI 能力可控可信"}]}', 3),
('culture', 'achievements', '{"items": []}', 4),
('culture', 'jobs', '{"items": []}', 5);

INSERT IGNORE INTO `site_contents` (`section`, `content_key`, `content_value`, `sort_order`) VALUES
('downloads', 'platforms', '{"items": [{"name": "Windows 客户端", "icon": "Monitor", "desc": "Windows 10+", "url": "#", "version": "v1.4.58", "size": ""}, {"name": "macOS 客户端", "icon": "Laptop", "desc": "macOS 12+", "url": "#", "version": "v1.4.58", "size": ""}, {"name": "Studio Web", "icon": "Globe", "desc": "在线版，无需安装", "url": "#", "version": "v1.4.58", "size": ""}]}', 1);

INSERT IGNORE INTO `site_contents` (`section`, `content_key`, `content_value`, `sort_order`) VALUES
('site_settings', 'brand_name', '{"value": "金元·数智员工"}', 1),
('site_settings', 'brand_slogan', '{"value": "从数字 Agent 出发，向数字人演进"}', 2),
('site_settings', 'contact_email', '{"value": "contact@jyfc.com"}', 3),
('site_settings', 'contact_phone', '{"value": "400-888-8888"}', 4),
('site_settings', 'contact_address', '{"value": "北京市朝阳区金元大厦"}', 5),
('site_settings', 'social_links', '{"items": [{"name": "微信公众号", "url": "#", "icon": "MessageCircle"}, {"name": "微博", "url": "#", "icon": "Twitter"}, {"name": "LinkedIn", "url": "#", "icon": "Linkedin"}]}', 6);
