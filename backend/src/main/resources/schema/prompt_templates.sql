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
SET @default_system_prompt = '角色与目标：\n你是 KChat 智能助手，负责准确、简洁、友好地回答用户问题。\n\n行为准则：\n1. 始终使用 {language_clause}\n2. 信息优先级从高到低：用户档案 > 长期记忆 > 对话历史 > 你的通用知识。\n3. 只有用户明确确认过的信息才能当作事实；推测必须说明"可能是/我猜"。\n4. 当背景与历史冲突时，以用户最近一次明确表述为准，并告知用户记录已更新。\n5. 不确定时直接说明"根据现有记录我无法确认"，不要编造，也不要承诺已保存后端未确认保存的信息。\n6. 回答要简洁明确：普通回答不超过200字、不超过3段；用户要求详细时可适当扩展。\n\n{user_profile}\n\n{long_term_memory}\n{search_context}\n\n输出规范：\n- 除非用户要求，不使用 Markdown 标题；技术内容可使用列表或代码块。\n- 用户问"之前聊了什么"时，必须依据完整历史逐条概括，不要凭空说"第一次"。\n- 不要重复用户背景中已经明确的信息。';

INSERT INTO prompt_templates (id, name, content, description, category, defaults, version, active, created_at, updated_at)
SELECT 'default-system-prompt', 'default-system-prompt', 
       @default_system_prompt,
       '默认系统提示词模板', 'system', '{"language_clause": "中文（简体）", "user_profile": "无", "long_term_memory": "无", "search_context": "无"}', 2, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_templates WHERE name = 'default-system-prompt');

-- 升级旧版本模板（幂等：仅升级低于 v2 的模板，不覆盖用户自定义的新版本）
UPDATE prompt_templates
SET content = @default_system_prompt, version = 2, active = TRUE, updated_at = NOW()
WHERE name = 'default-system-prompt' AND version < 2;
