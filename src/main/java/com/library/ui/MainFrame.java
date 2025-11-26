package com.library.ui;

import com.library.dao.UserDAO;
import com.library.entity.User;
import com.library.exception.BusinessException;
import com.library.util.SessionManager;
import com.library.exception.DBException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class MainFrame extends JFrame {

    private JTabbedPane tabs;
    private User currentUser;

    // ★ 引入 DAO
    private UserDAO userDAO = new UserDAO();

    public MainFrame(User user) {
        this.currentUser = user;
        setTitle("图书馆管理系统 - 当前用户: " + user.getUsername());
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createMenuBar();

        tabs = new JTabbedPane();

        // 核心分离逻辑：根据角色加载标签页 (保持不变)
        if ("admin".equals(user.getRole())) {
            // ========== 管理员界面 ==========
            tabs.addTab("📚 图书管理", new BookPanel(user));
            tabs.addTab("📊 借阅查询", new AdminStatusPanel());
            tabs.addTab("⏰ 超期遗失", new OverdueManagementPanel());
            tabs.addTab("📈 数据统计", new DashboardPanel());
            tabs.addTab("👥 用户管理", new UserManagerPanel());
            tabs.addTab("📝 系统日志", new LogViewerPanel());
            tabs.addTab("👤 个人中心", new PersonalCenterPanel(this));
        } else {
            // ========== 普通用户界面 ==========
            tabs.addTab("📚 借阅图书", new BorrowBookPanel(user));
            tabs.addTab("📖 归还图书", new ReturnBookPanel(user));
            tabs.addTab("📋 我的借阅记录", new MyBorrowPanel(user));
            tabs.addTab("👤 个人中心", new PersonalCenterPanel(this));
        }

        add(tabs);
    }

    /**
     * 创建菜单栏，分离“注销账户”（永久禁用）和“返回登录界面”（临时退出）
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu accountMenu = new JMenu("账户/系统");



        // 1. 返回登录界面 (仅清除会话)
        JMenuItem logoutItem = new JMenuItem("退出登录", KeyEvent.VK_R);
        logoutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

        // 2. 退出系统
        JMenuItem exitItem = new JMenuItem("退出系统", KeyEvent.VK_Q);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        // 3. 注销账户 (永久禁用功能)
        JMenuItem deactivateItem = new JMenuItem("注销账户", KeyEvent.VK_D);
        deactivateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

        // --- 监听器 ---

        // 【注销账户】操作：执行数据库禁用和退出
        deactivateItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "警告：【注销账户】将永久禁用您的账号，您将无法再次登录！\n确定要继续吗？",
                    "永久注销确认",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                performDeactivationAndLogout(); // ★ 调用禁用方法
            }
        });

        // 【返回登录界面】操作：执行简单退出登录
        logoutItem.addActionListener(e -> {
            performSimpleLogout(); // ★ 调用简单退出方法
        });

        // 退出系统操作
        exitItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "确定要退出整个系统吗？",
                    "退出确认",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });

        // --- 菜单组装 ---
        accountMenu.add(deactivateItem);
        accountMenu.add(logoutItem);
        accountMenu.addSeparator();
        accountMenu.add(exitItem);
        menuBar.add(accountMenu);

        setJMenuBar(menuBar);
    }

    /**
     * ★★★ 核心实现：执行数据库禁用和退出登录 ★★★
     * ✅ 改进：捕获 BusinessException，处理未归还图书的情况
     */
    private void performDeactivationAndLogout() {
        int userId = this.currentUser.getId();

        try {
            // 1. 在数据库中禁用当前用户 (设置 is_active = -1)
            // ✅ 此方法现在会检查是否有未归还图书
            userDAO.deactivateUser(userId);

            // 2. 清除内存中的会话
            SessionManager.clearSession();

            // 3. 提示成功信息
            JOptionPane.showMessageDialog(this,
                    "账户已成功注销。\n该账号已被永久禁用，您将无法再次使用其登录。",
                    "注销成功",
                    JOptionPane.INFORMATION_MESSAGE);

            // 4. 返回登录界面
            returnToLoginScreen();

        } catch (BusinessException ex) {
            // ✅ 新增：处理业务异常（未归还图书）
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "无法注销",
                    JOptionPane.WARNING_MESSAGE);
            // 不执行退出登录，用户可以继续使用

        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "注销失败: 无法连接数据库或禁用操作失败。请联系管理员。",
                    "数据库错误",
                    JOptionPane.ERROR_MESSAGE);

            // 即使 DB 失败，仍清除会话并退出当前窗口，防止信息泄露
            SessionManager.clearSession();
            returnToLoginScreen();
        }
    }

    /**
     * 简单退出登录：只清除会话并返回登录界面 (不禁用数据库账号)
     */
    private void performSimpleLogout() {
        SessionManager.clearSession();
        returnToLoginScreen();
    }

    /**
     * 公共方法：清理资源并返回登录界面
     */
    private void returnToLoginScreen() {
        // 1. 清除当前标签页引用
        if (tabs != null) {
            tabs.removeAll();
        }

        // 2. 返回登录界面
        new LoginFrame().setVisible(true);

        // 3. 关闭当前主窗口
        dispose();
    }


    /**
     * 允许其他面板更新主窗口标题（例如在用户名修改成功后）。
     */
    public void updateTitle(String newTitle) {
        setTitle(newTitle);
    }
}