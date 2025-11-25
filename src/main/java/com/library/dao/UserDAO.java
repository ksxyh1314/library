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
     * @param newPassword 新密码
     * @throws BusinessException 如果验证失败
     */
    private void validatePassword(String newPassword) throws BusinessException {
        if (newPassword.length() <= 6) {
            throw new BusinessException("新密码必须大于6个字符。");
        }
    }

    /**
     * 检查新密码是否与旧密码相同。
     * [注意: 此方法直接操作明文密码。]
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return 如果相同返回true，否则返回false
     */
    private boolean isSameAsOldPassword(int userId, String newPassword) throws DBException {
        // 查询数据库中的明文密码字段
        String sql = "SELECT password FROM users WHERE id = ?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String oldPassword = rs.getString("password");
                    // 直接比较新旧明文密码是否一致
                    return oldPassword.equals(newPassword);
                }
                return false; // 用户不存在
            }
        } catch (SQLException e) {
            throw new DBException("查询旧密码失败: " + e.getMessage(), e);
        }
    }

    /**
     * [核心功能] 更新指定用户的用户名和密码。
     * 满足：1. 密码大于6个字符。 2. 密码不和以前的密码相同。
     * @param userId 目标用户ID
     * @param newUsername 新用户名
     * @param newPassword 新密码
     * @throws DBException 数据库错误
     * @throws BusinessException 业务规则错误 (密码长度, 密码重复, 用户名重复)
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
            // 检查是否为用户名重复的异常
            if (e.getSQLState().startsWith("23")) {
                throw new BusinessException("用户名 [" + newUsername + "] 已存在，请更换。");
            }
            throw new DBException("更新用户凭证失败: " + e.getMessage(), e);
        }
    }

    // =================================================================
    // ★ 个人中心和管理员重置凭证所需的新增方法 END
    // =================================================================

    // --- 以下是您原有的方法 ---

    public User login(String username, String password) throws AuthException, ValidationException, DBException {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new ValidationException("用户名和密码不能为空。");
        }

        String sql = "SELECT * FROM users WHERE username=? AND password=? AND is_active=1";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
                    logDAO.logOperation("用户 [" + username + "] 登录成功");
                    return user;
                } else {
                    logDAO.logOperation("尝试登录失败: " + username);
                    // 检查是否是被禁用的用户
                    if(isUserDisabled(username)) {
                        throw new AuthException("账号已被禁用，请联系管理员。");
                    }
                    throw new AuthException("用户名或密码错误。");
                }
            }
        } catch (SQLException e) {
            throw new DBException("登录查询失败。", e);
        }
    }

    private boolean isUserDisabled(String username) throws DBException {
        String sql = "SELECT is_active FROM users WHERE username=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("is_active") == 0;
            }
        } catch (SQLException e) {
            throw new DBException("检查用户状态失败。", e);
        }
    }

    public DefaultTableModel getAllUsersModel() {
        Vector<String> cols = new Vector<>();
        cols.add("ID"); cols.add("用户名"); cols.add("角色"); cols.add("状态");
        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT id, username, role, is_active FROM users ORDER BY id";
        try (Connection conn = DBHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("username"));
                String roleCn = "admin".equals(rs.getString("role")) ? "管理员" : "普通用户";
                row.add(roleCn);
                String statusCn = rs.getInt("is_active") == 1 ? "启用" : "禁用";
                row.add(statusCn);
                data.add(row);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return new DefaultTableModel(data, cols);
    }

    public void addUser(String username, String password, String role) throws DBException, ValidationException {
        // ... (省略输入校验代码) ...
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

    // 您的原有方法 updatePassword 将不再需要，因为 updateUserCredentials 包含了密码修改功能
    public void updatePassword(int userId, String newPassword) throws DBException, ValidationException {
        // ... (省略输入校验代码) ...
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

    public void deleteUser(int userId) throws DBException {
        String sql = "DELETE FROM users WHERE id=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new DBException("删除失败，用户可能不存在");
            logDAO.logOperation("删除用户 ID: " + userId);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1451) {
                throw new DBException("删除失败：该用户有未处理的借阅记录，请先处理。", e);
            }
            throw new DBException("删除用户失败: " + e.getMessage(), e);
        }
    }

    public void updateUserStatus(int userId, int isActive) throws DBException {
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
    public void deactivateUser(int userId) throws DBException {
        // SQL: 假设您的用户表名为 users，且包含 is_active 字段
        String sql = "UPDATE users SET is_active = 0 WHERE id = ?";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            // 捕获 SQL 异常，封装为 DBException 抛出
            throw new DBException("注销用户账号失败，请检查数据库连接或表结构。", e);
        }
    }
}