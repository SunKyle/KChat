-- Prompt 模板表
CREATE TABLE IF NOT EXISTS prompt_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    description VARCHAR(500),
    category VARCHAR(50),
    defaults JSON,
    version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_prompt_templates_name (name),
    INDEX idx_prompt_templates_active (active),
    INDEX idx_prompt_templates_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入默认系统模板
INSERT INTO prompt_templates (id, name, content, description, category, defaults, version, active, created_at, updated_at)
SELECT 'default-system-prompt', 'default-system-prompt', 
       '你是一个智能助手。{language_clause}请根据以下用户背景信息回答问题。\n\n用户背景：\n{long_term_memory}',
       '默认系统提示词模板', 'system', '{"language_clause": "", "long_term_memory": "无"}', 1, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_templates WHERE name = 'default-system-prompt');