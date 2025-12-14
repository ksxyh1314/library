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
        // ★ 使用 SwingWorker 实现异步登录，避免界面卡顿
        SwingWorker<User, Void> worker = new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() throws Exception {
                // 在后台线程执行登录
                return userDAO.login(username, password);
            }

            @Override
            protected void done() {
                try {
                    User user = get();

                    // 检查实际登录的角色是否符合预期的按钮角色
                    if (!user.getRole().equals(expectedRole)) {
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                "您的账号身份是 [" + ("admin".equals(user.getRole()) ? "管理员" : "普通用户") +
                                        "]，请使用正确的登录按钮。",
                                "身份不匹配",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    // ★ 登录成功，显示确认弹窗
                    showSuccessDialog(user);

                } catch (Exception ex) {
                    // 处理异常
                    Throwable cause = ex.getCause();

                    if (cause instanceof ValidationException) {
                        // 验证异常（用户名或密码为空）
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                cause.getMessage(),
                                "错误",
                                JOptionPane.ERROR_MESSAGE);

                    } else if (cause instanceof AuthException) {
                        // ★ 认证失败（用户名或密码错误、账号被禁用/注销）
                        JPanel messagePanel = new JPanel();
                        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
                        messagePanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));

                        JLabel lblError = new JLabel(cause.getMessage());
                        lblError.setAlignmentX(Component.CENTER_ALIGNMENT);

                        JLabel lblHint = new JLabel("如果没有账号，可以先注册账号");
                        lblHint.setFont(new Font("微软雅黑", Font.PLAIN, 11));
                        lblHint.setForeground(Color.GRAY);
                        lblHint.setAlignmentX(Component.CENTER_ALIGNMENT);

                        messagePanel.add(lblError);
                        messagePanel.add(Box.createVerticalStrut(15));
                        messagePanel.add(lblHint);

                        JOptionPane.showMessageDialog(LoginFrame.this,
                                messagePanel,
                                "登录失败",
                                JOptionPane.ERROR_MESSAGE);

                    } else if (cause instanceof DBException) {
                        // 数据库异常
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                cause.getMessage(),
                                "系统错误",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        // 其他未知异常
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                "登录失败：" + ex.getMessage(),
                                "错误",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        };

        // 执行异步任务
        worker.execute();
    }

    /**
     * ★ 显示登录成功对话框
     */
    private void showSuccessDialog(User user) {
        String roleDisplay = "admin".equals(user.getRole()) ? "管理员" : "普通用户";

        // 创建自定义成功面板
        JPanel successPanel = new JPanel();
        successPanel.setLayout(new BoxLayout(successPanel, BoxLayout.Y_AXIS));
        successPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // ✓ 成功图标和标题
        JLabel lblWelcome = new JLabel("✓ 登录成功！");
        lblWelcome.setFont(new Font("微软雅黑", Font.BOLD, 16));
        lblWelcome.setForeground(new Color(40, 167, 69)); // 绿色
        lblWelcome.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 用户信息
        JLabel lblUserInfo = new JLabel("欢迎您，" + user.getUsername());
        lblUserInfo.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        lblUserInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 角色信息
        JLabel lblRoleInfo = new JLabel("身份：" + roleDisplay);
        lblRoleInfo.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        lblRoleInfo.setForeground(new Color(0, 102, 204)); // 蓝色
        lblRoleInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 组装面板
        successPanel.add(lblWelcome);
        successPanel.add(Box.createVerticalStrut(12));
        successPanel.add(lblUserInfo);
        successPanel.add(Box.createVerticalStrut(5));
        successPanel.add(lblRoleInfo);

        // 显示成功对话框（带确定/取消按钮）
        int result = JOptionPane.showConfirmDialog(this,
                successPanel,
                "登录成功",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        // ★ 用户点击"确定"后才进入主界面
        if (result == JOptionPane.OK_OPTION) {
            SessionManager.setCurrentUser(user);

            // ★ 使用 SwingUtilities.invokeLater 确保 UI 更新流畅
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    new MainFrame(user).setVisible(true);
                    dispose();
                }
            });
        }
        // 如果点击"取消"或关闭对话框，则留在登录界面
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
            // ★ 设置系统外观
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // ★ 启用抗锯齿和优化渲染
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // ★ 启用硬件加速（如果支持）
        System.setProperty("sun.java2d.opengl", "true");
        System.setProperty("sun.java2d.d3d", "true");

        // ★ 优化文本渲染质量
        System.setProperty("awt.useSystemAAFontSettings", "lcd");

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoginFrame().setVisible(true);
            }
        });
    }
}
