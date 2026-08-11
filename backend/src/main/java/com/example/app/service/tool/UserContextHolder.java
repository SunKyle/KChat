package com.example.app.service.tool;

/**
 * 线程级用户上下文。
 *
 * 在 Agent 工具执行期间临时保存当前请求的 userId，供工具内部读取用户的
 * 自定义配置（如工具箱为某工具配置的默认模型）。工具由 LangChain4j 反射调用，
 * 无法通过方法参数注入 userId，故用 ThreadLocal 在调用前后设置与清理。
 */
public final class UserContextHolder {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(String userId) {
        USER_ID.set(userId);
    }

    public static String get() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
