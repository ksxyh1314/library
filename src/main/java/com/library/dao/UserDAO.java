package com.library.dao;

import com.library.entity.User;
import com.library.exception.*;
import com.library.util.DBHelper;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.Vector;

public class UserDAO {
    private LogDAO logDAO = new LogDAO();

    // =================================================================
    // ★ 个人中心和管理员重置凭证所需的新增方法 START
    // =================================================================

    /**
     * 验证新的密码是否满足长度要求 (> 6个字符)。
     */
    private void validatePassword(String newPassword) throws BusinessException {
        if (newPassword.length() <= 6) {
            throw new BusinessException("新密码必须大于6个字符。");
        }
    }

    /**
     * 检查新密码是否与旧密码相同。
     */
    private boolean isSameAsOldPassword(int userId, String newPassword) throws DBException {
        String sql = "SELECT password FROM users WHERE id = ?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String oldPassword = rs.getString("password");
                    return oldPassword.equals(newPassword);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new DBException("查询旧密码失败: " + e.getMessage(), e);
        }
    }

    /**
     * [核心功能] 更新指定用户的用户名和密码。
     */
    public void updateUserCredentials(int userId, String newUsername, String newPassword) throws DBException, BusinessException {
        // 1. 验证密码长度 (> 6个字符)
        validatePassword(newPassword);

        // 2. 检查密码是否重复 (不和旧密码相同)
        if (isSameAsOldPassword(userId, newPassword)) {
            throw new BusinessException("新密码不能与旧密码相同。");
        }

        // 3. 执行更新
        String sql = "UPDATE users SET username = ?, password = ? WHERE id = ?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newUsername);
            ps.setString(2, newPassword);
            ps.setInt(3, userId);

            if (ps.executeUpdate() == 0) {
                throw new BusinessException("用户ID不存在，更新失败。");
            }

            logDAO.logOperation("更新了用户ID: " + userId + " 的凭证。新用户名: " + newUsername);

        } catch (SQLException e) {
            if (e.getSQLState().startsWith("23")) {
                throw new BusinessException("用户名 [" + newUsername + "] 已存在，请更换。");
            }
            throw new DBException("更新用户凭证失败: " + e.getMessage(), e);
        }
    }

    // =================================================================
    // ★ 个人中心和管理员重置凭证所需的新增方法 END
    // =================================================================

    /**
     * ✅ 用户登录验证
     * 改进：区分"已注销"和"已禁用"两种状态
     */
    public User login(String username, String password) throws AuthException, ValidationException, DBException {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new ValidationException("用户名和密码不能为空。");
        }

        // ✅ 改进：查询时只检查用户名和密码，不限制 is_active
        String sql = "SELECT * FROM users WHERE username=? AND password=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int isActive = rs.getInt("is_active");

                    // ✅ 关键改进：根据不同状态返回不同错误信息
                    if (isActive == -1) {
                        // 已注销状态
                        logDAO.logOperation("尝试登录已注销账号: " + username);
                        throw new AuthException("该账号已注销，无法登录。");
                    } else if (isActive == 0) {
                        // 被管理员禁用
                        logDAO.logOperation("尝试登录已禁用账号: " + username);
                        throw new AuthException("账号已被管理员禁用，请联系管理员。");
                    }

                    // 正常登录
                    User user = new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
                    logDAO.logOperation("用户 [" + username + "] 登录成功");
                    return user;
                } else {
                    logDAO.logOperation("尝试登录失败: " + username);
                    throw new AuthException("用户名或密码错误。");
                }
            }
        } catch (SQLException e) {
            throw new DBException("登录查询失败。", e);
        }
    }

    /**
     * ✅ 获取所有用户列表（用于管理界面）
     * 改进：状态列显示"正常"、"已禁用"、"已注销"
     */
    public DefaultTableModel getAllUsersModel() {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("用户名");
        cols.add("角色");
        cols.add("状态");
        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT id, username, role, is_active FROM users ORDER BY id";
        try (Connection conn = DBHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("username"));

                // 角色显示
                String roleCn = "admin".equals(rs.getString("role")) ? "管理员" : "普通用户";
                row.add(roleCn);

                // ✅ 关键改进：状态列显示三种状态
                int isActive = rs.getInt("is_active");
                String statusCn;
                if (isActive == 1) {
                    statusCn = "正常";
                } else if (isActive == 0) {
                    statusCn = "已禁用";
                } else {
                    statusCn = "已注销"; // is_active = -1
                }
                row.add(statusCn);

                data.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new DefaultTableModel(data, cols);
    }

    /**
     * 添加新用户
     */
    public void addUser(String username, String password, String role) throws DBException, ValidationException {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("用户名不能为空。");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("密码不能为空。");
        }

        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.executeUpdate();
            logDAO.logOperation("添加新用户: " + username + ", 角色: " + role);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DBException("用户 '" + username + "' 已存在。", e);
            }
            throw new DBException("添加用户失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重置密码（管理员功能）
     */
    public void updatePassword(int userId, String newPassword) throws DBException, ValidationException {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new ValidationException("密码不能为空。");
        }

        String sql = "UPDATE users SET password=? WHERE id=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setInt(2, userId);
            ps.executeUpdate();
            logDAO.logOperation("重置用户 ID " + userId + " 的密码");
        } catch (SQLException e) {
            throw new DBException("修改密码失败: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ 删除用户（增强版）
     * 改进：
     * 1. 检查用户是否有未归还的图书
     * 2. 检查用户是否有待支付的罚款（超期罚款、遗失罚款）
     * 3. 只要所有图书已归还且所有罚款已支付，就允许删除（即使有历史借阅记录）
     * 4. 记录详细的日志信息
     */
    public void deleteUser(int userId) throws DBException, BusinessException {
        Connection conn = null;
        String username = "用户ID:" + userId; // 默认值

        try {
            conn = DBHelper.getConnection();

            // ✅ 第一步：获取用户名（用于日志记录和错误提示）
            String getUsernameSql = "SELECT username FROM users WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(getUsernameSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        username = rs.getString("username");
                    } else {
                        throw new DBException("删除失败：用户不存在");
                    }
                }
            }

            // ✅ 第二步：检查是否有未归还的图书
            String checkBorrowSql = "SELECT COUNT(*) AS unreturned_count FROM borrow_records " +
                    "WHERE user_id = ? AND is_returned = 0";

            int unreturnedCount = 0;
            try (PreparedStatement checkPs = conn.prepareStatement(checkBorrowSql)) {
                checkPs.setInt(1, userId);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        unreturnedCount = rs.getInt("unreturned_count");
                    }
                }
            }

            // ✅ 如果有未归还图书，记录日志并抛出业务异常
            if (unreturnedCount > 0) {
                logDAO.logOperation(String.format(
                        "尝试删除用户 [%s] (ID:%d) 失败：存在 %d 本未归还图书",
                        username, userId, unreturnedCount
                ));

                throw new BusinessException(
                        String.format("删除失败：用户 [%s] 还有 %d 本图书未归还。\n\n" +
                                        "请先让用户归还所有图书后再进行删除操作。",
                                username, unreturnedCount)
                );
            }

            // ✅ 第三步：检查是否有待支付的罚款
            String checkFineSql = "SELECT COUNT(*) AS unpaid_fine_count FROM borrow_records " +
                    "WHERE user_id = ? AND fine_amount > 0 AND fine_paid = 0";

            int unpaidFineCount = 0;
            try (PreparedStatement checkPs = conn.prepareStatement(checkFineSql)) {
                checkPs.setInt(1, userId);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        unpaidFineCount = rs.getInt("unpaid_fine_count");
                    }
                }
            }

            // ✅ 如果有待支付罚款，记录日志并抛出业务异常
            if (unpaidFineCount > 0) {
                // 查询具体的罚款详情
                String fineDetailsSql = "SELECT SUM(fine_amount) AS total_fine FROM borrow_records " +
                        "WHERE user_id = ? AND fine_amount > 0 AND fine_paid = 0";

                double totalFine = 0;
                try (PreparedStatement ps = conn.prepareStatement(fineDetailsSql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            totalFine = rs.getDouble("total_fine");
                        }
                    }
                }

                logDAO.logOperation(String.format(
                        "尝试删除用户 [%s] (ID:%d) 失败：存在 %d 笔待支付罚款，总计 %.2f 元",
                        username, userId, unpaidFineCount, totalFine
                ));

                throw new BusinessException(
                        String.format("删除失败：用户 [%s] 还有 %d 笔罚款待支付（总计 %.2f 元）。\n\n" +
                                        "请先让用户支付所有罚款后再进行删除操作。\n" +
                                        "罚款类型可能包括：超期罚款、遗失罚款等。",
                                username, unpaidFineCount, totalFine)
                );
            }

            // ✅ 第四步：执行删除操作（此时允许删除，即使有已归还的借阅记录）
            String deleteSql = "DELETE FROM users WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, userId);
                int rows = ps.executeUpdate();

                if (rows == 0) {
                    throw new DBException("删除失败：用户可能不存在");
                }

                // ✅ 记录成功日志
                logDAO.logOperation(String.format(
                        "成功删除用户 [%s] (ID:%d)",
                        username, userId
                ));
            }

        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (SQLException e) {
            // ✅ 记录数据库异常日志
            logDAO.logOperation(String.format(
                    "删除用户 [%s] (ID:%d) 失败：数据库错误 - %s",
                    username, userId, e.getMessage()
            ));

            // 外键约束错误（虽然已经检查了未归还图书，但保留此判断作为双保险）
            if (e.getErrorCode() == 1451) {
                throw new DBException("删除失败：该用户存在关联数据（借阅记录等），请先处理。", e);
            }
            throw new DBException("删除用户失败: " + e.getMessage(), e);
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * ✅ 管理员启用/禁用用户（0或1）
     * 注意：此方法不应用于"已注销"状态的用户
     */
    public void updateUserStatus(int userId, int isActive) throws DBException {
        // ✅ 增加校验：不允许对已注销用户执行启用/禁用
        String checkSql = "SELECT is_active FROM users WHERE id=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, userId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && rs.getInt("is_active") == -1) {
                    throw new DBException("该用户已注销，无法执行启用/禁用操作。");
                }
            }
        } catch (SQLException e) {
            throw new DBException("检查用户状态失败: " + e.getMessage(), e);
        }

        String sql = "UPDATE users SET is_active=? WHERE id=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, isActive);
            ps.setInt(2, userId);
            ps.executeUpdate();
            String status = isActive == 1 ? "启用" : "禁用";
            logDAO.logOperation("设置用户 ID " + userId + " 状态为: " + status);
        } catch (SQLException e) {
            throw new DBException("更新用户状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ 用户注销自己的账号（设置 is_active = -1）
     * 此操作不可逆，用户将永久无法登录
     *
     * ✅ 新增校验：如果用户有未归还的图书，无法注销
     * ✅ 日志记录：成功或失败都记录到系统日志
     */
    public void deactivateUser(int userId) throws DBException, BusinessException {
        Connection conn = null;
        String username = "用户ID:" + userId; // 默认值

        try {
            conn = DBHelper.getConnection();

            // ✅ 获取用户名（用于日志记录）
            String getUsernameSql = "SELECT username FROM users WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(getUsernameSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        username = rs.getString("username");
                    }
                }
            }

            // ✅ 第一步：检查是否有未归还的图书
            String checkSql = "SELECT COUNT(*) AS unreturned_count FROM borrow_records " +
                    "WHERE user_id = ? AND return_time IS NULL";

            int unreturnedCount = 0;
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setInt(1, userId);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        unreturnedCount = rs.getInt("unreturned_count");
                    }
                }
            }

            // ✅ 如果有未归还图书，记录日志并抛出业务异常
            if (unreturnedCount > 0) {
                // ✅ 记录失败日志
                logDAO.logOperation(String.format(
                        "用户 [%s] (ID:%d) 尝试注销账号失败：存在 %d 本未归还图书",
                        username, userId, unreturnedCount
                ));

                throw new BusinessException(
                        String.format("注销失败：您还有 %d 本图书未归还。\n\n请先归还所有图书后再进行注销操作。",
                                unreturnedCount)
                );
            }

            // ✅ 第二步：执行注销操作
            String updateSql = "UPDATE users SET is_active = -1 WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setInt(1, userId);
                int rows = pstmt.executeUpdate();

                if (rows == 0) {
                    // ✅ 记录失败日志
                    logDAO.logOperation(String.format(
                            "用户 [%s] (ID:%d) 注销账号失败：用户不存在",
                            username, userId
                    ));
                    throw new DBException("注销失败：用户不存在。");
                }

                // ✅ 记录成功日志
                logDAO.logOperation(String.format(
                        "用户 [%s] (ID:%d) 已成功注销账号（永久禁用）",
                        username, userId
                ));
            }

        } catch (BusinessException e) {
            // ✅ 业务异常直接抛出（已在上面记录日志）
            throw e;
        } catch (SQLException e) {
            // ✅ 记录数据库异常日志
            logDAO.logOperation(String.format(
                    "用户 [%s] (ID:%d) 注销账号失败：数据库错误 - %s",
                    username, userId, e.getMessage()
            ));
            throw new DBException("注销用户账号失败，请检查数据库连接或表结构。", e);
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * ★ 新增：根据用户ID查询完整的用户信息（包括密码）
     * 用于个人中心只修改用户名时获取当前密码
     */
    public User getUserById(int userId) throws DBException {
        String sql = "SELECT id, username, password, role, is_active FROM users WHERE id = ?";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("role")
                    );
                    return user;
                } else {
                    throw new DBException("用户ID " + userId + " 不存在");
                }
            }

        } catch (SQLException e) {
            throw new DBException("查询用户信息失败: " + e.getMessage(), e);
        }
    }

}