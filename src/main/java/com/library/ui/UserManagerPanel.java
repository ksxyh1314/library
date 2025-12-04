package com.library.ui;

import com.library.dao.UserDAO;
import com.library.exception.DBException;
import com.library.exception.ValidationException;
import com.library.exception.BusinessException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.regex.Pattern;

/**
 * 用户与权限管理面板
 * ★ 优化：界面样式与其他面板一致
 * ✅ 新增：防止管理员删除自己的账号
 * ✅ 新增：删除用户前检查未归还图书
 */
public class UserManagerPanel extends JPanel {
    private UserDAO userDAO = new UserDAO();
    private JTable userTable;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel statsLabel;
    private JTextField searchField;

    // ✅ 新增：当前登录用户的ID（用于防止删除自己）
    private int currentUserId;

    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * ✅ 修改构造函数：传入当前登录用户的ID
     * @param currentUserId 当前登录管理员的用户ID
     */
    public UserManagerPanel(int currentUserId) {
        this.currentUserId = currentUserId;
        initializeUI();
    }

    /**
     * ✅ 向后兼容：保留无参构造函数（设置默认值为-1表示未知）
     * 建议使用带参数的构造函数
     */
    public UserManagerPanel() {
        this.currentUserId = -1; // -1 表示未设置当前用户ID
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        // ============================================================
        // 1. 顶部面板
        // ============================================================
        JPanel topPanel = new JPanel(new BorderLayout());

        // --- 标题面板 ---
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("👥 用户与权限管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // --- 搜索面板 ---
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.add(new JLabel("用户名全称:"));
        searchField = new JTextField(15);
        searchPanel.add(searchField);

        JButton btnSearch = new JButton("🔍 搜索用户");
        searchPanel.add(btnSearch);

        JButton btnReset = new JButton("↺ 重置");
        searchPanel.add(btnReset);

        // --- 操作按钮面板 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton btnAdd = new JButton("➕ 添加用户");
        JButton btnResetPass = new JButton("🔑 重置密码为 " + DEFAULT_PASSWORD);
        JButton btnDelete = new JButton("🗑️ 删除用户");
        JButton btnToggleStatus = new JButton("🔄 启用/禁用");
        JButton btnRefresh = new JButton("🔄 刷新列表");

        buttonPanel.add(btnAdd);
        buttonPanel.add(btnResetPass);
        buttonPanel.add(btnDelete);
        buttonPanel.add(btnToggleStatus);
        buttonPanel.add(btnRefresh);

        // --- 组合控制面板 ---
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(titlePanel, BorderLayout.NORTH);
        controlPanel.add(searchPanel, BorderLayout.CENTER);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);

        topPanel.add(controlPanel, BorderLayout.CENTER);

        // --- 提示信息 ---
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📋 提示：可精准搜索用户名 | 状态说明：「正常」可操作 | 「已禁用」管理员禁用 | 「已注销」用户自己注销（仅可删除）");
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // ============================================================
        // 2. 中间表格
        // ============================================================
        userTable = new JTable();
        userTable.getTableHeader().setReorderingAllowed(false);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setRowHeight(28);
        userTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        refreshTable();

        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // ============================================================
        // 3. 底部统计信息
        // ============================================================
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(240, 240, 240));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============================================================
        // 4. 事件监听
        // ============================================================

        btnSearch.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());

        btnReset.addActionListener(e -> {
            searchField.setText("");
            userTable.clearSelection();
            performSearch();
        });

        btnRefresh.addActionListener(e -> {
            refreshTable();
            JOptionPane.showMessageDialog(this, "数据已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        btnAdd.addActionListener(e -> addUserAction());
        btnResetPass.addActionListener(e -> resetPasswordAction());
        btnDelete.addActionListener(e -> deleteUserAction());
        btnToggleStatus.addActionListener(e -> toggleUserStatusAction());

        updateStats();
    }

    /**
     * 刷新表格数据
     */
    private void refreshTable() {
        model = userDAO.getAllUsersModel();
        userTable.setModel(model);

        // 调整列宽
        if (userTable.getColumnCount() > 0) {
            // 用户ID
            userTable.getColumnModel().getColumn(0).setPreferredWidth(80);
            userTable.getColumnModel().getColumn(0).setMinWidth(60);

            // 用户名
            userTable.getColumnModel().getColumn(1).setPreferredWidth(200);
            userTable.getColumnModel().getColumn(1).setMinWidth(150);

            // 角色
            userTable.getColumnModel().getColumn(2).setPreferredWidth(120);
            userTable.getColumnModel().getColumn(2).setMinWidth(80);

            // 状态
            userTable.getColumnModel().getColumn(3).setPreferredWidth(120);
            userTable.getColumnModel().getColumn(3).setMinWidth(80);
        }

        // ★★★ 关键：使用 AUTO_RESIZE_SUBSEQUENT_COLUMNS 铺满界面
        userTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // 左对齐
        javax.swing.table.DefaultTableCellRenderer leftRenderer = new javax.swing.table.DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

        for (int i = 0; i < userTable.getColumnCount(); i++) {
            userTable.getColumnModel().getColumn(i).setCellRenderer(leftRenderer);
        }

        sorter = new TableRowSorter<>(model);
        userTable.setRowSorter(sorter);

        if (searchField != null) {
            searchField.setText("");
        }

        updateStats();
    }

    /**
     * 执行精准搜索（完全匹配用户名）
     */
    private void performSearch() {
        if (sorter == null) {
            return;
        }

        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            RowFilter<DefaultTableModel, Object> filter =
                    RowFilter.regexFilter("(?i)^" + Pattern.quote(searchText) + "$", 1);
            sorter.setRowFilter(filter);

            if (userTable.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this,
                        "未找到用户名为 [" + searchText + "] 的用户。\n\n" +
                                "提示：请输入完整的用户名（精准匹配）",
                        "搜索结果",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }

        updateStats();
    }

    /**
     * 更新底部统计信息
     */
    private void updateStats() {
        if (statsLabel == null || userTable == null) {
            return;
        }

        int totalCount = userTable.getRowCount();
        int adminCount = 0;
        int userCount = 0;
        int normalCount = 0;
        int disabledCount = 0;
        int deactivatedCount = 0;

        for (int i = 0; i < totalCount; i++) {
            String role = (String) userTable.getValueAt(i, 2);
            String status = (String) userTable.getValueAt(i, 3);

            if ("管理员".equals(role)) {
                adminCount++;
            } else {
                userCount++;
            }

            if ("正常".equals(status)) {
                normalCount++;
            } else if ("已禁用".equals(status)) {
                disabledCount++;
            } else if ("已注销".equals(status)) {
                deactivatedCount++;
            }
        }

        String statsText = String.format(
                "当前显示: %d 人  |  管理员: %d 人  |  普通用户: %d 人  |  正常: %d 人  |  已禁用: %d 人  |  已注销: %d 人",
                totalCount, adminCount, userCount, normalCount, disabledCount, deactivatedCount
        );
        statsLabel.setText(statsText);

        if (deactivatedCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43));
        } else if (disabledCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34));
        } else {
            statsLabel.setForeground(new Color(39, 174, 96));
        }
    }

    /**
     * 添加用户
     */
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

    /**
     * 重置密码
     */
    private void resetPasswordAction() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要重置密码的用户。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = userTable.convertRowIndexToModel(row);
        int userId = (int) userTable.getModel().getValueAt(modelRow, 0);
        String username = (String) userTable.getModel().getValueAt(modelRow, 1);
        String status = (String) userTable.getModel().getValueAt(modelRow, 3);

        if ("已注销".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    "该用户已注销，无法重置密码。\n如需恢复使用，请删除后重新创建账号。",
                    "操作限制",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

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

    /**
     * ✅ 删除用户（增强版）
     * 改进：
     * 1. 防止管理员删除自己的账号
     * 2. 显示更详细的确认信息
     * 3. 区分已注销用户的提示信息
     * 4. 处理未归还图书和待支付罚款的情况
     * 5. 允许删除已归还所有图书且已支付所有罚款的用户（即使有历史记录）
     */
    private void deleteUserAction() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "请先选择要删除的用户。",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = userTable.convertRowIndexToModel(row);
        int userId = (int) userTable.getModel().getValueAt(modelRow, 0);
        String username = (String) userTable.getModel().getValueAt(modelRow, 1);
        String role = (String) userTable.getModel().getValueAt(modelRow, 2);
        String status = (String) userTable.getModel().getValueAt(modelRow, 3);

        // ✅ 关键改进1：防止管理员删除自己的账号
        if (currentUserId > 0 && userId == currentUserId) {
            JOptionPane.showMessageDialog(this,
                    "⛔ 不能删除自己的账号！\n\n" +
                            "如需删除当前账号，请联系其他管理员操作。",
                    "操作限制",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ✅ 改进2：区分不同状态的确认提示
        String confirmMessage;
        if ("已注销".equals(status)) {
            confirmMessage = String.format(
                    "确认删除已注销用户 [%s] 吗？\n\n" +
                            "⚠️ 此操作将永久删除该用户的所有数据！\n" +
                            "包括：用户信息、所有借阅历史记录等。\n\n" +
                            "✅ 删除前会自动检查：\n" +
                            "• 是否有未归还的图书\n" +
                            "• 是否有待支付的罚款",
                    username
            );
        } else {
            confirmMessage = String.format(
                    "确认删除用户 [%s] 吗？\n\n" +
                            "⚠️ 警告：此操作不可撤销！\n\n" +
                            "用户信息：\n" +
                            "• 用户名：%s\n" +
                            "• 角色：%s\n" +
                            "• 状态：%s\n\n" +
                            "✅ 删除前会自动检查：\n" +
                            "• 是否有未归还的图书\n" +
                            "• 是否有待支付的罚款（超期/遗失）\n\n" +
                            "只要所有图书已归还且罚款已支付，就可以删除。\n" +
                            "历史借阅记录将一并清除。",
                    username, username, role, status
            );
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                confirmMessage,
                "删除确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                userDAO.deleteUser(userId);

                // ✅ 成功提示
                JOptionPane.showMessageDialog(this,
                        String.format("用户 [%s] 删除成功！\n\n该用户的所有数据已被清除。", username),
                        "删除成功",
                        JOptionPane.INFORMATION_MESSAGE);

                refreshTable();

            } catch (BusinessException ex) {
                // ✅ 业务异常（如有未归还图书）
                JOptionPane.showMessageDialog(this,
                        ex.getMessage(),
                        "删除失败",
                        JOptionPane.WARNING_MESSAGE);

            } catch (DBException ex) {
                // ✅ 数据库异常
                JOptionPane.showMessageDialog(this,
                        "删除失败：" + ex.getMessage() + "\n\n请检查数据库连接或联系技术支持。",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 启用/禁用用户
     */
    private void toggleUserStatusAction() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要操作的用户。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = userTable.convertRowIndexToModel(row);
        int userId = (int) userTable.getModel().getValueAt(modelRow, 0);
        String username = (String) userTable.getModel().getValueAt(modelRow, 1);
        String currentStatusCn = (String) userTable.getModel().getValueAt(modelRow, 3);

        if ("已注销".equals(currentStatusCn)) {
            JOptionPane.showMessageDialog(this,
                    "该用户已注销，无法执行启用/禁用操作。\n\n" +
                            "已注销账户已永久失效，仅可执行【删除】操作。\n" +
                            "如需恢复使用，请删除后重新创建账号。",
                    "操作限制",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int newStatus = "正常".equals(currentStatusCn) ? 0 : 1;
        String action = newStatus == 1 ? "启用" : "禁用";

        int confirm = JOptionPane.showConfirmDialog(this,
                "确认对用户 [" + username + "] 执行 [" + action + "] 操作吗？",
                "状态切换确认",
                JOptionPane.YES_NO_OPTION);

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