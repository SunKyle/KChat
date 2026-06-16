-- V3__create_notes_todos_tables.sql
-- 创建笔记表
CREATE TABLE IF NOT EXISTS notes (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    category VARCHAR(50) DEFAULT '默认',
    tags JSON,
    pinned BOOLEAN DEFAULT FALSE,
    memory_id VARCHAR(36),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_notes_user_id (user_id),
    INDEX idx_notes_user_pinned (user_id, pinned),
    INDEX idx_notes_user_updated (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建待办表
CREATE TABLE IF NOT EXISTS todos (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'medium',
    due_date DATETIME,
    category VARCHAR(50) DEFAULT '默认',
    memory_id VARCHAR(36),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    completed_at DATETIME,
    INDEX idx_todos_user_id (user_id),
    INDEX idx_todos_user_status (user_id, status),
    INDEX idx_todos_user_priority (user_id, priority),
    INDEX idx_todos_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
