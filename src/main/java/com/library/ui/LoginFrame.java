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
        setSize(350, 250);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new GridLayout(4, 1, 10, 10));

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

        // 添加组件到 Frame
        add(title);
        add(p1);
        add(p2);
        add(p3);

        // ============ 监听器 ============

        // 管理员登录监听
        btnLoginAdmin.addActionListener(e -> {
            performLogin(txtUser.getText(), new String(txtPass.getPassword()), "admin");
        });

        // 普通用户登录监听
        btnLoginUser.addActionListener(e -> {
            performLogin(txtUser.getText(), new String(txtPass.getPassword()), "user");
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

        } catch (ValidationException | AuthException | DBException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}