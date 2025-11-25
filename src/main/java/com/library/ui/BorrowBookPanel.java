package com.library.ui;

import com.library.dao.BookDAO;
import com.library.entity.User;
import com.library.exception.BusinessException;
import com.library.exception.DBException;

import javax.swing.*;
import java.awt.*;
import javax.swing.table.DefaultTableModel;

/**
 * 借书面板
 * 功能：
 * 1. 显示所有可借阅的图书。
 * 2. 提供关键词搜索。
 * 3. 底部显示可借阅图书总数统计。
 */
public class BorrowBookPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable bookTable;
    private User currentUser;

    // UI 组件
    private JTextField txtSearch;
    private JButton btnSearch;
    private JButton btnResetSearch;

    // ★ 底部统计标签
    private JLabel statsLabel;

    public BorrowBookPanel(User user) {
        this.currentUser = user;
        setLayout(new BorderLayout());

        // ============================================================
        // 1. 顶部搜索 + 按钮区域
        // ============================================================
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        txtSearch = new JTextField(20);
        btnSearch = new JButton("🔍 搜索图书");
        btnResetSearch = new JButton("↺ 重置");
        JButton btnBorrow = new JButton("📥 借阅选中图书");

        topPanel.add(new JLabel("书名关键词:"));
        topPanel.add(txtSearch);
        topPanel.add(btnSearch);
        topPanel.add(btnResetSearch);
        topPanel.add(btnBorrow);

        // ============================================================
        // 2. 提示信息区域
        // ============================================================
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📚 以下为可借阅图书列表，选择后点击【借阅选中图书】按钮进行借阅");
        infoLabel.setForeground(new Color(52, 152, 219)); // 蓝色提示，代表借入
        infoPanel.add(infoLabel);

        // 组合顶部容器
        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // ============================================================
        // 3. 中间表格区域
        // ============================================================
        bookTable = new JTable();
        bookTable.getTableHeader().setReorderingAllowed(false);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(bookTable), BorderLayout.CENTER);

        // ============================================================
        // 4. ★ 底部统计信息区域
        // ============================================================
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(245, 245, 245)); // 浅灰背景
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        statsLabel = new JLabel("正在加载数据...");
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        statsLabel.setForeground(new Color(0, 102, 204)); // 蓝色文字，与借阅主题一致

        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============================================================
        // 5. 事件监听器
        // ============================================================
        btnSearch.addActionListener(e -> refreshTable(txtSearch.getText()));

        btnResetSearch.addActionListener(e -> {
            txtSearch.setText("");
            refreshTable(null);
        });

        btnBorrow.addActionListener(e -> borrowBookAction());

        // 初始加载数据
        refreshTable(null);
    }

    /**
     * 刷新表格数据并更新底部统计
     * @param keyword 搜索关键词
     */
    private void refreshTable(String keyword) {
        // 调用 DAO 获取仅包含"可借阅"图书的数据模型
        DefaultTableModel model = bookDAO.getBookModel(keyword, true);
        bookTable.setModel(model);

        // 搜索结果为空的提示
        if (model.getRowCount() == 0 && keyword != null && !keyword.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "未找到关键词 [" + keyword + "] 的可借阅图书。",
                    "搜索结果", JOptionPane.INFORMATION_MESSAGE);
        }

        // ★ 更新底部统计
        updateStats();
    }

    /**
     * 更新底部统计文字
     */
    private void updateStats() {
        int count = bookTable.getRowCount();
        statsLabel.setText("当前可借阅图书数量: " + count + " 本");
    }

    /**
     * 借阅图书动作
     */
    private void borrowBookAction() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要借阅的图书。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 获取选中行的数据 (假设 Col 0=ID, Col 1=书名, Col 2=作者, Col 3=状态)
        int bookId = (int) bookTable.getValueAt(row, 0);
        String title = (String) bookTable.getValueAt(row, 1);
        String author = (String) bookTable.getValueAt(row, 2);
        String status = (String) bookTable.getValueAt(row, 3);

        // 双重检查状态
        if (!"可借阅".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    "该书当前状态为 [" + status + "]，无法借阅。",
                    "操作失败", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 确认对话框
        String message = String.format(
                "确认借阅以下图书吗？\n\n书名：%s\n作者：%s\n图书ID：%d",
                title, author, bookId
        );

        int confirm = JOptionPane.showConfirmDialog(this, message, "借阅确认", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // 调用 DAO 执行借阅
                bookDAO.borrowBook(bookId, currentUser.getId());

                // 成功后刷新列表
                refreshTable(null);
                txtSearch.setText(""); // 清空搜索框

                JOptionPane.showMessageDialog(this,
                        "图书 [" + title + "] 借阅成功！",
                        "成功", JOptionPane.INFORMATION_MESSAGE);

            } catch (DBException | BusinessException ex) {
                JOptionPane.showMessageDialog(this,
                        "借阅失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}