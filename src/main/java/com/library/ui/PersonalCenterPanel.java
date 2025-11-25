package com.library.ui;

import com.library.dao.UserDAO;
import com.library.entity.User;
import com.library.exception.BusinessException;
import com.library.exception.DBException;
import com.library.util.SessionManager;
import javax.swing.*;
import java.awt.*;

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
        infoPanel.add(new JLabel("用户ID:"), new JLabel(" "));
        infoPanel.add(lblUserId);

        lblUsername = new JLabel(user.getUsername());
        infoPanel.add(new JLabel("当前用户名:"));
        infoPanel.add(lblUsername);

        lblRole = new JLabel(user.getRole().equals("admin") ? "管理员" : "普通用户");
        infoPanel.add(new JLabel("账户角色:"));
        infoPanel.add(lblRole);

        // --- 中部修改表单 ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("修改用户名和密码"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 新用户名
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("新用户名:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        txtNewUsername = new JTextField(user.getUsername(), 20);
        formPanel.add(txtNewUsername, gbc);

        // 新密码
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        formPanel.add(new JLabel("新密码 (需 > 6字符):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        txtNewPassword = new JPasswordField(20);
        formPanel.add(txtNewPassword, gbc);

        // 确认密码
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        formPanel.add(new JLabel("确认密码:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        txtConfirmPassword = new JPasswordField(20);
        formPanel.add(txtConfirmPassword, gbc);

        // --- 底部操作按钮 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton btnUpdate = new JButton("确认更新");
        JButton btnReset = new JButton("重置表单");

        buttonPanel.add(btnUpdate);
        buttonPanel.add(btnReset);

        // ============ 监听器 ============
        btnUpdate.addActionListener(e -> updateCredentialsAction(user));
        btnReset.addActionListener(e -> resetForm(user));

        add(infoPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void resetForm(User user) {
        txtNewUsername.setText(user.getUsername());
        txtNewPassword.setText("");
        txtConfirmPassword.setText("");
    }

    private void updateCredentialsAction(User user) {
        String newUsername = txtNewUsername.getText().trim();
        String newPassword = new String(txtNewPassword.getPassword());
        String confirmPassword = new String(txtConfirmPassword.getPassword());

        if (newUsername.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this, "用户名、新密码和确认密码都不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this, "两次输入的密码不一致！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // ★ 调用 UserDAO.updateUserCredentials (已实现的业务逻辑)
            userDAO.updateUserCredentials(user.getId(), newUsername, newPassword);

            JOptionPane.showMessageDialog(this, "凭证更新成功！请注意您的新用户名和密码。", "成功", JOptionPane.INFORMATION_MESSAGE);

            // 1. ★ 更新内存中的User对象 (Requires User.setUsername)
            user.setUsername(newUsername);
            // 2. ★ 更新SessionManager (Requires SessionManager.setCurrentUser)
            SessionManager.setCurrentUser(user);
            // 3. ★ 更新主窗口标题 (Requires MainFrame.updateTitle)
            mainFrame.updateTitle("图书馆管理系统 - 当前用户: " + newUsername);

            // 4. 更新面板显示并清空密码输入框
            lblUsername.setText(newUsername);
            txtNewPassword.setText("");
            txtConfirmPassword.setText("");

        } catch (BusinessException ex) {
            JOptionPane.showMessageDialog(this, "更新失败: " + ex.getMessage(), "业务错误", JOptionPane.WARNING_MESSAGE);
        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this, "更新失败: 数据库错误。", "系统错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}