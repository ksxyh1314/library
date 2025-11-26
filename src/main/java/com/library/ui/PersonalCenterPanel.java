package com.library.ui;

import com.library.dao.UserDAO;
import com.library.entity.User;
import com.library.exception.BusinessException;
import com.library.exception.DBException;
import com.library.util.SessionManager;
import javax.swing.*;
import java.awt.*;
import java.util.regex.Pattern;

public class PersonalCenterPanel extends JPanel {
    private UserDAO userDAO = new UserDAO();
    private MainFrame mainFrame;

    // 用户信息显示
    private JLabel lblUsername;
    private JLabel lblRole;
    private JLabel lblUserId;

    // 表单输入元素
    private JTextField txtNewUsername;
    private JPasswordField txtNewPassword;
    private JPasswordField txtConfirmPassword;

    // 显示/隐藏密码的复选框
    private JCheckBox chkShowPassword;

    public PersonalCenterPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ★ 使用修正后的 SessionManager.getCurrentUser()
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            add(new JLabel("会话错误，请重新登录。"), BorderLayout.CENTER);
            return;
        }

        // --- 顶部信息展示 ---
        JPanel infoPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("账户信息"));

        lblUserId = new JLabel(String.valueOf(user.getId()));
        infoPanel.add(new JLabel("用户ID:"));
        infoPanel.add(lblUserId);

        lblUsername = new JLabel(user.getUsername());
        infoPanel.add(new JLabel("当前用户名:"));
        infoPanel.add(lblUsername);

        lblRole = new JLabel(user.getRole().equals("admin") ? "管理员" : "普通用户");
        infoPanel.add(new JLabel("账户角色:"));
        infoPanel.add(lblRole);

        // --- 中部修改表单 ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("修改账户信息"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ========== 用户名部分 ==========
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        formPanel.add(new JLabel("新用户名:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        txtNewUsername = new JTextField(20);
        txtNewUsername.setToolTipText("输入新用户名，留空则不修改");
        formPanel.add(txtNewUsername, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        JLabel lblUsernameHint = new JLabel("提示：输入新用户名，留空则不修改");
        lblUsernameHint.setFont(new Font(lblUsernameHint.getFont().getName(), Font.ITALIC, 11));
        lblUsernameHint.setForeground(new Color(100, 100, 100));
        formPanel.add(lblUsernameHint, gbc);

        // 分隔线
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.insets = new Insets(15, 8, 15, 8);
        formPanel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(8, 8, 8, 8);

        // ========== 密码部分 ==========
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        formPanel.add(new JLabel("新密码:"), gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0;
        txtNewPassword = new JPasswordField(20);
        txtNewPassword.setEchoChar('●');
        txtNewPassword.setToolTipText("密码需大于6个字符，包含字母和数字");
        formPanel.add(txtNewPassword, gbc);

        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0;
        JLabel lblPasswordHint = new JLabel("提示：密码需大于6个字符，包含字母和数字，留空则不修改");
        lblPasswordHint.setFont(new Font(lblPasswordHint.getFont().getName(), Font.ITALIC, 11));
        lblPasswordHint.setForeground(new Color(100, 100, 100));
        formPanel.add(lblPasswordHint, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        formPanel.add(new JLabel("确认密码:"), gbc);

        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1.0;
        txtConfirmPassword = new JPasswordField(20);
        txtConfirmPassword.setEchoChar('●');
        txtConfirmPassword.setToolTipText("再次输入新密码进行确认");
        formPanel.add(txtConfirmPassword, gbc);

        gbc.gridx = 1; gbc.gridy = 6; gbc.weightx = 1.0;
        chkShowPassword = new JCheckBox("显示密码");
        chkShowPassword.addActionListener(e -> togglePasswordVisibility());
        formPanel.add(chkShowPassword, gbc);

        // --- 底部操作按钮 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton btnUpdate = new JButton("确认更新");
        JButton btnReset = new JButton("重置表单");

        btnUpdate.setPreferredSize(new Dimension(120, 35));
        btnReset.setPreferredSize(new Dimension(120, 35));

        buttonPanel.add(btnUpdate);
        buttonPanel.add(btnReset);

        // ============ 监听器 ============
        btnUpdate.addActionListener(e -> updateCredentialsAction(user));
        btnReset.addActionListener(e -> resetForm(user));

        add(infoPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * 切换密码显示/隐藏状态
     */
    private void togglePasswordVisibility() {
        if (chkShowPassword.isSelected()) {
            txtNewPassword.setEchoChar((char) 0);
            txtConfirmPassword.setEchoChar((char) 0);
        } else {
            txtNewPassword.setEchoChar('●');
            txtConfirmPassword.setEchoChar('●');
        }
    }

    /**
     * 重置表单
     */
    private void resetForm(User user) {
        txtNewUsername.setText("");
        txtNewPassword.setText("");
        txtConfirmPassword.setText("");
        chkShowPassword.setSelected(false);
        togglePasswordVisibility();
    }

    /**
     * ★ 新增：验证密码复杂性
     * 规则：
     * 1. 长度大于6个字符
     * 2. 必须包含字母
     * 3. 必须包含数字
     */
    private boolean validatePasswordComplexity(String password) {
        // 长度检查
        if (password.length() <= 6) {
            return false;
        }

        // 必须包含字母（大写或小写）
        boolean hasLetter = Pattern.compile("[a-zA-Z]").matcher(password).find();

        // 必须包含数字
        boolean hasDigit = Pattern.compile("[0-9]").matcher(password).find();

        return hasLetter && hasDigit;
    }

    /**
     * ★ 新增：检查新密码是否与旧密码相同
     */
    private boolean isSameAsOldPassword(int userId, String newPassword) throws DBException {
        User currentUserData = userDAO.getUserById(userId);
        if (currentUserData == null || currentUserData.getPassword() == null) {
            return false;
        }
        return currentUserData.getPassword().equals(newPassword);
    }

    /**
     * 更新凭证操作 - 核心逻辑
     */
    private void updateCredentialsAction(User user) {
        String newUsername = txtNewUsername.getText().trim();
        String newPassword = new String(txtNewPassword.getPassword()).trim();
        String confirmPassword = new String(txtConfirmPassword.getPassword()).trim();

        // ========== 第一步：判断用户想要修改什么 ==========
        boolean wantUpdateUsername = !newUsername.isEmpty();
        boolean wantUpdatePassword = !newPassword.isEmpty() || !confirmPassword.isEmpty();

        // 如果什么都不修改
        if (!wantUpdateUsername && !wantUpdatePassword) {
            JOptionPane.showMessageDialog(this,
                    "请至少修改用户名或密码中的一项。",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // ========== 第二步：验证用户名（如果要修改） ==========
        if (wantUpdateUsername) {
            if (newUsername.equals(user.getUsername())) {
                JOptionPane.showMessageDialog(this,
                        "新用户名与当前用户名相同，无需修改。",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        // ========== 第三步：验证密码（如果要修改） ==========
        if (wantUpdatePassword) {
            // 检查两个密码框是否都填写了
            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "修改密码时，新密码和确认密码都必须填写！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ★ 密码长度验证
            if (newPassword.length() <= 6) {
                JOptionPane.showMessageDialog(this,
                        "新密码长度必须大于 6 个字符！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ★ 密码复杂性验证
            if (!validatePasswordComplexity(newPassword)) {
                JOptionPane.showMessageDialog(this,
                        "密码复杂性不足！\n\n密码必须满足以下条件：\n" +
                                "• 长度大于 6 个字符\n" +
                                "• 至少包含一个字母（a-z 或 A-Z）\n" +
                                "• 至少包含一个数字（0-9）",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ★ 检查是否与旧密码相同
            try {
                if (isSameAsOldPassword(user.getId(), newPassword)) {
                    JOptionPane.showMessageDialog(this,
                            "新密码不能与旧密码相同！\n\n请设置一个不同的密码。",
                            "错误",
                            JOptionPane.ERROR_MESSAGE);
                    txtNewPassword.setText("");
                    txtConfirmPassword.setText("");
                    return;
                }
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this,
                        "验证旧密码时出错：\n" + ex.getMessage(),
                        "系统错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 两次密码一致性验证
            if (!newPassword.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(this,
                        "两次输入的密码不一致，请重新输入！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                txtNewPassword.setText("");
                txtConfirmPassword.setText("");
                return;
            }
        }

        // ========== 第四步：执行更新操作 ==========
        try {
            String finalUsername;
            String finalPassword;

            // 情况1：同时修改用户名和密码
            if (wantUpdateUsername && wantUpdatePassword) {
                finalUsername = newUsername;
                finalPassword = newPassword;

                // ★ 直接更新，不调用 UserDAO.updateUserCredentials()
                // 因为它会再次验证密码（与旧密码相同的检查会重复）
                updateUsernameAndPassword(user.getId(), finalUsername, finalPassword);

                JOptionPane.showMessageDialog(this,
                        "更新成功！\n\n✓ 用户名已更新为：" + finalUsername + "\n✓ 密码已更新",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);

                // 更新用户名相关显示
                user.setUsername(finalUsername);
                SessionManager.setCurrentUser(user);
                mainFrame.updateTitle("图书馆管理系统 - 当前用户: " + finalUsername);
                lblUsername.setText(finalUsername);
            }
            // 情况2：只修改用户名
            else if (wantUpdateUsername) {
                finalUsername = newUsername;

                // ★ 关键：从数据库获取当前密码
                User currentUserData = userDAO.getUserById(user.getId());
                if (currentUserData == null || currentUserData.getPassword() == null) {
                    throw new BusinessException("无法获取当前密码信息");
                }
                finalPassword = currentUserData.getPassword();

                // ★ 使用自定义方法，只更新用户名
                updateUsernameOnly(user.getId(), finalUsername);

                JOptionPane.showMessageDialog(this,
                        "更新成功！\n\n✓ 用户名已更新为：" + finalUsername,
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);

                // 更新用户名相关显示
                user.setUsername(finalUsername);
                SessionManager.setCurrentUser(user);
                mainFrame.updateTitle("图书馆管理系统 - 当前用户: " + finalUsername);
                lblUsername.setText(finalUsername);
            }
            // 情况3：只修改密码
            else if (wantUpdatePassword) {
                finalUsername = user.getUsername();
                finalPassword = newPassword;

                // ★ 只更新密码
                updatePasswordOnly(user.getId(), finalPassword);

                JOptionPane.showMessageDialog(this,
                        "更新成功！\n\n✓ 密码已更新",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            // 清空表单
            resetForm(user);

        } catch (BusinessException ex) {
            JOptionPane.showMessageDialog(this,
                    "更新失败：\n" + ex.getMessage(),
                    "业务错误",
                    JOptionPane.WARNING_MESSAGE);
        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "更新失败：数据库错误\n" + ex.getMessage(),
                    "系统错误",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "更新失败：未知错误\n" + ex.getMessage(),
                    "系统错误",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /**
     * ★ 新增：只更新用户名（绕过密码验证）
     */
    private void updateUsernameOnly(int userId, String newUsername)
            throws DBException, BusinessException {

        String sql = "UPDATE users SET username = ? WHERE id = ?";

        try (java.sql.Connection conn = com.library.util.DBHelper.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newUsername);
            ps.setInt(2, userId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected == 0) {
                throw new BusinessException("用户ID不存在，更新失败。");
            }

            // 记录日志
            new com.library.dao.LogDAO().logOperation(
                    "用户ID: " + userId + " 更新了用户名为: " + newUsername
            );

        } catch (java.sql.SQLException e) {
            if (e.getSQLState().startsWith("23")) {
                throw new BusinessException("用户名 [" + newUsername + "] 已存在，请更换。");
            }
            throw new DBException("更新用户名失败: " + e.getMessage(), e);
        }
    }

    /**
     * ★ 新增：只更新密码（绕过密码验证）
     */
    private void updatePasswordOnly(int userId, String newPassword)
            throws DBException, BusinessException {

        String sql = "UPDATE users SET password = ? WHERE id = ?";

        try (java.sql.Connection conn = com.library.util.DBHelper.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newPassword);
            ps.setInt(2, userId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected == 0) {
                throw new BusinessException("用户ID不存在，更新失败。");
            }

            // 记录日志
            new com.library.dao.LogDAO().logOperation(
                    "用户ID: " + userId + " 更新了密码"
            );

        } catch (java.sql.SQLException e) {
            throw new DBException("更新密码失败: " + e.getMessage(), e);
        }
    }

    /**
     * ★ 新增：同时更新用户名和密码（绕过密码验证）
     */
    private void updateUsernameAndPassword(int userId, String newUsername, String newPassword)
            throws DBException, BusinessException {

        String sql = "UPDATE users SET username = ?, password = ? WHERE id = ?";

        try (java.sql.Connection conn = com.library.util.DBHelper.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newUsername);
            ps.setString(2, newPassword);
            ps.setInt(3, userId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected == 0) {
                throw new BusinessException("用户ID不存在，更新失败。");
            }

            // 记录日志
            new com.library.dao.LogDAO().logOperation(
                    "用户ID: " + userId + " 更新了用户名为: " + newUsername + " 并更新了密码"
            );

        } catch (java.sql.SQLException e) {
            if (e.getSQLState().startsWith("23")) {
                throw new BusinessException("用户名 [" + newUsername + "] 已存在，请更换。");
            }
            throw new DBException("更新用户凭证失败: " + e.getMessage(), e);
        }
    }
}
