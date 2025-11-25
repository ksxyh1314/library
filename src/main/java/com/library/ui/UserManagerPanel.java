package com.library.ui;

import com.library.dao.UserDAO;
import com.library.exception.DBException;
import com.library.exception.ValidationException;
import javax.swing.*;
import java.awt.*;

public class UserManagerPanel extends JPanel {
    // 依赖于 UserDAO
    private UserDAO userDAO = new UserDAO();
    private JTable userTable;
    private JLabel statsLabel;

    // ★ 定义默认密码常量
    private static final String DEFAULT_PASSWORD = "123456";

    public UserManagerPanel() {
        setLayout(new BorderLayout());

        // --- 顶部操作面板 ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // 标题面板
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("👥 用户与权限管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAdd = new JButton("➕ 添加用户");
        JButton btnResetPass = new JButton("🔑 重置密码为 " + DEFAULT_PASSWORD);
        JButton btnDelete = new JButton("❌ 删除用户");
        JButton btnToggleStatus = new JButton("🔄 启用/禁用");
        JButton btnRefresh = new JButton("🔄 刷新列表");

        buttonPanel.add(btnAdd);
        buttonPanel.add(btnResetPass);
        buttonPanel.add(btnDelete);
        buttonPanel.add(btnToggleStatus);
        buttonPanel.add(new JLabel("  ")); // 间隔
        buttonPanel.add(btnRefresh);

        topPanel.add(titlePanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- 中间表格 ---
        userTable = new JTable();
        userTable.getTableHeader().setReorderingAllowed(false);
        refreshTable();
        add(new JScrollPane(userTable), BorderLayout.CENTER);

        // --- 底部统计信息 ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(240, 240, 240));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============ 事件监听 ============

        btnRefresh.addActionListener(e -> {
            refreshTable();
            JOptionPane.showMessageDialog(this, "数据已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        btnAdd.addActionListener(e -> addUserAction());

        btnResetPass.addActionListener(e -> resetPasswordAction());

        btnDelete.addActionListener(e -> deleteUserAction());

        btnToggleStatus.addActionListener(e -> toggleUserStatusAction());

        // ★ 初始化时更新统计信息
        updateStats();
    }

    // --- 刷新表格辅助方法 ---
    private void refreshTable() {
        userTable.setModel(userDAO.getAllUsersModel());

        // ★ 刷新后更新统计信息
        updateStats();
    }

    /**
     * ★ 更新底部统计信息
     */
    private void updateStats() {
        if (statsLabel == null || userTable == null) {
            return;
        }

        int totalCount = userTable.getRowCount();
        int adminCount = 0;
        int userCount = 0;
        int enabledCount = 0;
        int disabledCount = 0;

        // 统计用户信息
        for (int i = 0; i < totalCount; i++) {
            // 角色在第2列 (索引1)
            String role = (String) userTable.getValueAt(i, 2);
            // 状态在第3列 (索引3)
            String status = (String) userTable.getValueAt(i, 3);

            if ("管理员".equals(role)) {
                adminCount++;
            } else {
                userCount++;
            }

            if ("启用".equals(status)) {
                enabledCount++;
            } else {
                disabledCount++;
            }
        }

        // 显示统计信息
        String statsText = String.format(
                "总用户数: %d 人  |  管理员: %d 人  |  普通用户: %d 人  |  已启用: %d 人  |  已禁用: %d 人",
                totalCount, adminCount, userCount, enabledCount, disabledCount
        );
        statsLabel.setText(statsText);

        // 根据状态设置颜色
        if (disabledCount > 0) {
            // 有禁用用户 - 深红色（警示）
            statsLabel.setForeground(new Color(192, 0, 0));
        } else if (adminCount == 0 || userCount == 0) {
            // 缺少某类用户 - 深绿色（提醒）
            statsLabel.setForeground(new Color(0, 102, 0));
        } else {
            // 正常状态 - 深橙色
            statsLabel.setForeground(new Color(204, 102, 0));
        }
    }

    // --- 1. 添加用户逻辑 ---
    private void addUserAction() {
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JComboBox<String> roleSelector = new JComboBox<>(new String[]{"普通用户", "管理员"});

        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.add(new JLabel("用户名:"));
        panel.add(usernameField);
        panel.add(new JLabel("密码:"));
        panel.add(passwordField);
        panel.add(new JLabel("角色:"));
        panel.add(roleSelector);

        int result = JOptionPane.showConfirmDialog(this, panel, "添加新用户", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            String roleDisplay = (String) roleSelector.getSelectedItem();
            String role = "管理员".equals(roleDisplay) ? "admin" : "user";

            try {
                userDAO.addUser(username, password, role);
                JOptionPane.showMessageDialog(this, "用户 " + username + " 添加成功!", "成功", JOptionPane.INFORMATION_MESSAGE);
                refreshTable();
            } catch (DBException | ValidationException ex) {
                JOptionPane.showMessageDialog(this, "添加失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- 2. 重置密码逻辑（改为默认密码123456） ---
    private void resetPasswordAction() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要重置密码的用户。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ID 是表格的第 0 列，Username 是第 1 列
        int userId = (int) userTable.getValueAt(row, 0);
        String username = (String) userTable.getValueAt(row, 1);

        // ★ 修改：直接使用默认密码，显示确认对话框
        String message = String.format(
                "确认将用户 [%s] 的密码重置为默认密码吗？\n\n" +
                        "默认密码：%s\n\n" +
                        "重置后请提醒用户及时修改密码。",
                username, DEFAULT_PASSWORD
        );

        int confirm = JOptionPane.showConfirmDialog(this, message, "重置密码确认",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // ★ 使用默认密码重置
                userDAO.updatePassword(userId, DEFAULT_PASSWORD);

                JOptionPane.showMessageDialog(this,
                        String.format("用户 [%s] 的密码已成功重置为：%s\n请通知用户尽快修改密码。",
                                username, DEFAULT_PASSWORD),
                        "重置成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException | ValidationException ex) {
                JOptionPane.showMessageDialog(this, "重置密码失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- 3. 删除用户逻辑 ---
    private void deleteUserAction() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的用户。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int userId = (int) userTable.getValueAt(row, 0);
        String username = (String) userTable.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this, "确认删除用户 [" + username + "] 吗？", "删除确认", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                userDAO.deleteUser(userId);
                JOptionPane.showMessageDialog(this, "用户 [" + username + "] 删除成功!", "成功", JOptionPane.INFORMATION_MESSAGE);
                refreshTable();
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- 4. 启用/禁用账户逻辑 ---
    private void toggleUserStatusAction() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要操作的用户。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int userId = (int) userTable.getValueAt(row, 0);
        String username = (String) userTable.getValueAt(row, 1);
        // 状态是第 3 列 (中文显示：启用/禁用)
        String currentStatusCn = (String) userTable.getValueAt(row, 3);

        // 逻辑：如果是"启用"，则新状态为禁用(0)；如果是"禁用"，则新状态为启用(1)
        int newStatus = "启用".equals(currentStatusCn) ? 0 : 1;
        String action = newStatus == 1 ? "启用" : "禁用";

        int confirm = JOptionPane.showConfirmDialog(this, "确认对用户 [" + username + "] 执行 [" + action + "] 操作吗？", "状态切换确认", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                userDAO.updateUserStatus(userId, newStatus);
                JOptionPane.showMessageDialog(this, "用户 [" + username + "] 已成功" + action + "!", "成功", JOptionPane.INFORMATION_MESSAGE);
                refreshTable();
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this, "操作失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}