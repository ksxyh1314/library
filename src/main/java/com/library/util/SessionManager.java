package com.library.util;

import com.library.entity.User;

/**
 * 用于在应用程序中集中存储和获取当前登录用户的会话信息。
 * 在单线程 Swing 应用程序中，使用静态变量存储当前登录用户。
 */
public class SessionManager {

    // 使用一个静态变量存储当前登录的完整 User 对象
    private static User loggedInUser;

    /**
     * 在用户登录成功后设置当前登录的完整 User 对象。
     * 解决了 PersonalCenterPanel 中调用 setCurrentUser(User user) 的问题。
     */
    public static void setCurrentUser(User user) {
        loggedInUser = user;
    }

    /**
     * 获取当前登录的完整 User 对象。
     * 解决了 PersonalCenterPanel 中调用 getCurrentUser() 的问题。
     */
    public static User getCurrentUser() {
        return loggedInUser;
    }

    /**
     * 在用户登录成功后设置当前用户名 (兼容旧方法)。
     */
    public static void setCurrentUsername(String username) {
        // 兼容处理：如果loggedInUser存在，则更新其username
        if (loggedInUser != null) {
            // 注意：这要求 User 类必须要有 setUsername 方法
            loggedInUser.setUsername(username);
        }
    }

    /**
     * 在应用内任何地方获取当前用户名 (兼容旧方法)。
     */
    public static String getCurrentUsername() {
        if (loggedInUser != null) {
            return loggedInUser.getUsername();
        }
        return "SYSTEM_UNKWOWN";
    }

    /**
     * 在用户注销或会话结束时移除信息。
     */
    public static void clearSession() {
        loggedInUser = null;
    }
}