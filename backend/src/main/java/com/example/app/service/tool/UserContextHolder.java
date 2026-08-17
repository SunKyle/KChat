package com.example.app.service.tool;

/**
 * 线程级用户上下文。
 *
 * 在 Agent 工具执行期间临时保存当前请求的 userId，供工具内部读取用户的
 * 自定义配置（如工具箱为某工具配置的默认模型）。工具由 LangChain4j 反射调用，
 * 无法通过方法参数注入 userId，故用 ThreadLocal 在调用前后设置与清理。
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * try (var ignored = UserContextHolder.set(userId)) {
 *     // 工具执行逻辑
 * }
 * }</pre>
 *
 * <p>{@link #set(String)} 返回的 {@link AutoCloseable} 会在 {@code try-with-resources}
 * 退出时自动调用 {@link #clear()}，确保 ThreadLocal 不会泄漏。</p>
 */
public final class UserContextHolder {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /**
     * 设置当前线程的用户 ID，并返回一个 {@link AutoCloseable}，
     * 在 {@code try-with-resources} 退出时自动调用 {@link #clear()}。
     *
     * @param userId 用户 ID
     * @return 用于自动清理的 AutoCloseable
     */
    public static AutoCloseable set(String userId) {
        USER_ID.set(userId);
        return UserContextHolder::clear;
    }

    public static String get() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
