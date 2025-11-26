package com.library.ui;

import com.library.dao.UserDAO;
import com.library.entity.User;
import com.library.exception.*;
import com.library.util.SessionManager;
import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private UserDAO userDAO = new UserDAO();

    // 引用 UI 组件以便在 Listener 中获取值
    private JTextField txtUser;
    private JPasswordField txtPass;

    public LoginFrame() {
        setTitle("系统登录");
        setSize(400, 280);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new GridLayout(5, 1, 10, 10));

        JLabel title = new JLabel("图书馆管理系统", JLabel.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 18));

        // --- 1. 用户名输入 ---
        JPanel p1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        txtUser = new JTextField(15);
        p1.add(new JLabel("账号: "));
        p1.add(txtUser);

        // --- 2. 密码输入 ---
        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        txtPass = new JPasswordField(15);
        p2.add(new JLabel("密码: "));
        p2.add(txtPass);

        // --- 3. 登录按钮 (双按钮代替下拉框) ---
        JPanel p3 = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton btnLoginAdmin = new JButton("管理员登录");
        JButton btnLoginUser = new JButton("普通用户登录");
        p3.add(btnLoginAdmin);
        p3.add(btnLoginUser);

        // --- 4. 注册按钮 ---
        JPanel p4 = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton btnRegister = new JButton("还没有账号？点击注册");
        btnRegister.setForeground(new Color(0, 102, 204));
        btnRegister.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btnRegister.setBorderPainted(false);
        btnRegister.setContentAreaFilled(false);
        btnRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));
        p4.add(btnRegister);

        // 添加组件到 Frame
        add(title);
        add(p1);
        add(p2);
        add(p3);
        add(p4);

        // ============ 监听器 ============

        // 管理员登录监听
        btnLoginAdmin.addActionListener(e -> {
            performLogin(txtUser.getText(), new String(txtPass.getPassword()), "admin");
        });

        // 普通用户登录监听
        btnLoginUser.addActionListener(e -> {
            performLogin(txtUser.getText(), new String(txtPass.getPassword()), "user");
        });

        // 注册按钮监听
        btnRegister.addActionListener(e -> {
            openRegisterDialog();
        });

        // 允许按 Enter 键登录
        txtPass.addActionListener(e -> {
            performLogin(txtUser.getText(), new String(txtPass.getPassword()), "user");
        });
    }

    /**
     * 执行登录逻辑，并校验身份是否符合预期。
     * @param username 输入的用户名
     * @param password 输入的密码
     * @param expectedRole 预期的角色 ("admin" 或 "user")
     */
    private void performLogin(String username, String password, String expectedRole) {
        try {
            User user = userDAO.login(username, password);

            // 检查实际登录的角色是否符合预期的按钮角色
            if (!user.getRole().equals(expectedRole)) {
                String roleCn = "admin".equals(expectedRole) ? "管理员" : "普通用户";
                JOptionPane.showMessageDialog(this,
                        "您的账号身份是 [" + ("admin".equals(user.getRole()) ? "管理员" : "普通用户") + "]，请使用正确的登录按钮。",
                        "身份不匹配", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // ★ 登录成功，设置会话并跳转
            SessionManager.setCurrentUser(user);
            new MainFrame(user).setVisible(true);
            dispose();

        } catch (ValidationException ex) {
            // 验证异常（用户名或密码为空）
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);

        } catch (AuthException ex) {
            // ★ 认证失败（用户名或密码错误、账号被禁用/注销）
            // 创建自定义消息面板
            JPanel messagePanel = new JPanel();
            messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));

            // 主要错误信息
            JLabel lblError = new JLabel(ex.getMessage());
            lblError.setAlignmentX(Component.CENTER_ALIGNMENT);

            // 空白间隔
            messagePanel.add(lblError);
            messagePanel.add(Box.createVerticalStrut(15)); // 15像素间隔

            // ★ 灰色提示（不使用斜体）
            JLabel lblHint = new JLabel("如果没有账号，可以先注册账号");
            lblHint.setFont(new Font("微软雅黑", Font.PLAIN, 11)); // ← PLAIN 普通字体
            lblHint.setForeground(Color.GRAY); // 灰色
            lblHint.setAlignmentX(Component.CENTER_ALIGNMENT);

            messagePanel.add(lblHint);

            // 显示自定义消息对话框
            JOptionPane.showMessageDialog(this,
                    messagePanel,
                    "登录失败",
                    JOptionPane.ERROR_MESSAGE);

        } catch (DBException ex) {
            // 数据库异常
            JOptionPane.showMessageDialog(this, ex.getMessage(), "系统错误", JOptionPane.ERROR_MESSAGE);
        }
    }


    /**
     * ★ 打开用户注册对话框
     */
    private void openRegisterDialog() {
        JDialog registerDialog = new JDialog(this, "用户注册", true);
        registerDialog.setSize(400, 300);
        registerDialog.setLocationRelativeTo(this);
        registerDialog.setLayout(new BorderLayout(10, 10));

        // 标题
        JLabel lblTitle = new JLabel("新用户注册", JLabel.CENTER);
        lblTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
        lblTitle.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // 表单面板
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 用户名
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        formPanel.add(new JLabel("用户名:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        JTextField txtRegUsername = new JTextField(20);
        formPanel.add(txtRegUsername, gbc);

        // 密码
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        formPanel.add(new JLabel("密码:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        JPasswordField txtRegPassword = new JPasswordField(20);
        formPanel.add(txtRegPassword, gbc);

        // 确认密码
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        formPanel.add(new JLabel("确认密码:"), gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        JPasswordField txtRegConfirmPassword = new JPasswordField(20);
        formPanel.add(txtRegConfirmPassword, gbc);

        // 提示信息
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JLabel lblHint = new JLabel("<html><font color='gray'>• 密码需大于6个字符<br>• 必须包含字母和数字</font></html>");
        lblHint.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        formPanel.add(lblHint, gbc);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton btnConfirmRegister = new JButton("确认注册");
        JButton btnCancelRegister = new JButton("取消");

        btnConfirmRegister.setPreferredSize(new Dimension(100, 30));
        btnCancelRegister.setPreferredSize(new Dimension(100, 30));

        buttonPanel.add(btnConfirmRegister);
        buttonPanel.add(btnCancelRegister);

        // 添加到对话框
        registerDialog.add(lblTitle, BorderLayout.NORTH);
        registerDialog.add(formPanel, BorderLayout.CENTER);
        registerDialog.add(buttonPanel, BorderLayout.SOUTH);

        // ============ 注册按钮监听 ============
        btnConfirmRegister.addActionListener(e -> {
            String username = txtRegUsername.getText().trim();
            String password = new String(txtRegPassword.getPassword()).trim();
            String confirmPassword = new String(txtRegConfirmPassword.getPassword()).trim();

            // 验证输入
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(registerDialog,
                        "用户名不能为空！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(registerDialog,
                        "密码不能为空！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 密码长度验证
            if (password.length() <= 6) {
                JOptionPane.showMessageDialog(registerDialog,
                        "密码长度必须大于 6 个字符！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 密码复杂性验证
            if (!validatePasswordComplexity(password)) {
                JOptionPane.showMessageDialog(registerDialog,
                        "密码复杂性不足！\n\n密码必须满足以下条件：\n" +
                                "• 长度大于 6 个字符\n" +
                                "• 至少包含一个字母（a-z 或 A-Z）\n" +
                                "• 至少包含一个数字（0-9）",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 两次密码一致性验证
            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(registerDialog,
                        "两次输入的密码不一致！",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                txtRegPassword.setText("");
                txtRegConfirmPassword.setText("");
                return;
            }

            // 执行注册
            try {
                userDAO.addUser(username, password, "user"); // 默认注册为普通用户

                JOptionPane.showMessageDialog(registerDialog,
                        "注册成功！\n\n用户名：" + username + "\n\n请使用新账号登录。",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);

                // 关闭注册对话框，并自动填充用户名到登录框
                registerDialog.dispose();
                txtUser.setText(username);
                txtPass.setText("");
                txtPass.requestFocus();

            } catch (DBException ex) {
                JOptionPane.showMessageDialog(registerDialog,
                        "注册失败：\n" + ex.getMessage(),
                        "数据库错误",
                        JOptionPane.ERROR_MESSAGE);
            } catch (ValidationException ex) {
                JOptionPane.showMessageDialog(registerDialog,
                        "注册失败：\n" + ex.getMessage(),
                        "验证错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // 取消按钮监听
        btnCancelRegister.addActionListener(e -> {
            registerDialog.dispose();
        });

        registerDialog.setVisible(true);
    }

    /**
     * ★ 验证密码复杂性
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
        boolean hasLetter = password.matches(".*[a-zA-Z].*");

        // 必须包含数字
        boolean hasDigit = password.matches(".*[0-9].*");

        return hasLetter && hasDigit;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
