package com.library.config;

/**
 * 系统配置类
 * 用于管理全局配置参数，支持测试模式和生产模式切换
 */
public class SystemConfig {

    // ============================================================
    // ★ 测试模式开关（测试完成后改为 false）
    // ============================================================
    public static final boolean IS_TEST_MODE = false; // ← 测试时改为 true，生产时改为 false

    // ============================================================
    // ★ 借阅期限配置
    // ============================================================

    /**
     * 借阅期限（毫秒）
     * - 测试模式：1 分钟（60,000 毫秒）
     * - 生产模式：30 天（2,592,000,000 毫秒）
     */
    public static final long DUE_PERIOD_MILLIS = IS_TEST_MODE
            ? 1L * 60 * 1000                    // 测试：1 分钟
            : 30L * 24 * 60 * 60 * 1000;        // 生产：30 天

    /**
     * 借阅期限（文字描述）
     */
    public static final String DUE_PERIOD_TEXT = IS_TEST_MODE
            ? "1 分钟"
            : "30 天";

    /**
     * 罚款配置
     */
    public static final double FINE_PER_UNIT = 1.0;

    /**
     * 罚款单位说明
     */
    public static final String FINE_UNIT_TEXT = IS_TEST_MODE
            ? "每分钟"
            : "每天";

    /**
     * 获取当前模式说明
     */
    public static String getModeDescription() {
        if (IS_TEST_MODE) {
            return "【测试模式】借阅期限: " + DUE_PERIOD_TEXT ;
        } else {
            return "【生产模式】借阅期限: " + DUE_PERIOD_TEXT ;
        }
    }

    /**
     * 计算超期时长（根据测试模式返回分钟或天数）
     */
    public static long calculateOverduePeriod(long overdueMillis) {
        if (IS_TEST_MODE) {
            // 测试模式：返回超期分钟数
            return overdueMillis / (60 * 1000);
        } else {
            // 生产模式：返回超期天数
            return overdueMillis / (24 * 60 * 60 * 1000);
        }
    }

    /**
     * 计算剩余时长（根据测试模式返回分钟或天数）
     */
    public static long calculateRemainingPeriod(long remainingMillis) {
        if (IS_TEST_MODE) {
            // 测试模式：返回剩余分钟数
            return remainingMillis / (60 * 1000);
        } else {
            // 生产模式：返回剩余天数
            return remainingMillis / (24 * 60 * 60 * 1000);
        }
    }

    /**
     * 获取时间单位文字
     */
    public static String getTimeUnitText() {
        return IS_TEST_MODE ? "分钟" : "天";
    }
}
