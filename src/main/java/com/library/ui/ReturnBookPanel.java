package com.library.ui;

import com.library.dao.BookDAO;
import com.library.entity.User;
import com.library.exception.BusinessException;
import com.library.exception.DBException;

import javax.swing.*;
import java.awt.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * 还书面板
 * 特性：
 * 1. UI 风格与 BorrowBookPanel 保持一致（顶部搜索栏）。
 * 2. 保留了底部的统计信息栏。
 */
public class ReturnBookPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable bookTable;
    private User currentUser;

    // 搜索组件
    private JTextField txtSearch;
    private JButton btnSearch;
    private JButton btnResetSearch;

    // ★ 底部统计标签
    private JLabel statsLabel;

    public ReturnBookPanel(User user) {
        this.currentUser = user;
        setLayout(new BorderLayout());

        // ============================================================
        // 1. 顶部搜索 + 按钮区域
        // ============================================================
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        txtSearch = new JTextField(20);
        btnSearch = new JButton("🔍 搜索已借图书");
        btnResetSearch = new JButton("↺ 重置");
        JButton btnReturn = new JButton("📤 归还选中图书");

        topPanel.add(new JLabel("书名关键词:"));
        topPanel.add(txtSearch);
        topPanel.add(btnSearch);
        topPanel.add(btnResetSearch);
        topPanel.add(btnReturn);

        // ============================================================
        // 2. 提示信息区域
        // ============================================================
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📖 以下为当前未归还的图书，选择后点击【归还选中图书】按钮进行归还");
        infoLabel.setForeground(new Color(231, 76, 60)); // 红色提示，区分借书界面
        infoPanel.add(infoLabel);

        // 将 1 和 2 组合放在顶部
        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // ============================================================
        // 3. 表格区域
        // ============================================================
        bookTable = new JTable();
        bookTable.getTableHeader().setReorderingAllowed(false);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(bookTable), BorderLayout.CENTER);

        // ============================================================
        // 4. ★ 底部统计信息区域 (保留功能)
        // ============================================================
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(245, 245, 245)); // 浅灰背景
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // 内边距

        statsLabel = new JLabel("正在加载数据...");
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        statsLabel.setForeground(new Color(204, 102, 0)); // 深橙色文字

        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============================================================
        // 5. 事件监听
        // ============================================================
        btnSearch.addActionListener(e -> refreshTable(txtSearch.getText()));

        btnResetSearch.addActionListener(e -> {
            txtSearch.setText("");
            refreshTable(null);
        });

        btnReturn.addActionListener(e -> returnBookAction());

        // 初始化加载数据
        refreshTable(null);
    }

    /**
     * 刷新表格数据并更新底部统计
     */
    private void refreshTable(String keyword) {
        // 1. 获取数据模型
        DefaultTableModel model = bookDAO.getCurrentBorrowedBooksModel(currentUser.getId());
        bookTable.setModel(model);

        // 2. 客户端过滤 (实现本地搜索)
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        bookTable.setRowSorter(sorter);

        if (keyword != null && !keyword.trim().isEmpty()) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + keyword));
        } else {
            bookTable.setRowSorter(null);
        }

        // 3. ★ 更新底部统计数据
        updateStats();
    }

    /**
     * ★ 更新底部统计文字
     */
    private void updateStats() {
        int count = bookTable.getRowCount(); // 获取当前表格显示的行数
        statsLabel.setText("当前未归还图书数量: " + count + " 本");
    }

    /**
     * 归还图书动作
     */
    private void returnBookAction() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要归还的图书。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 转换视图行索引到模型行索引 (防止搜索后行号错乱)
        int modelRow = bookTable.convertRowIndexToModel(row);
        DefaultTableModel model = (DefaultTableModel) bookTable.getModel();

        // 假设 Column 0 是 ID, Column 1 是书名 (请根据您的 BookDAO 实际列顺序调整)
        int bookId = (int) model.getValueAt(modelRow, 0);
        String title = (String) model.getValueAt(modelRow, 1);

        String message = String.format("确认归还图书《%s》吗？", title);

        int confirm = JOptionPane.showConfirmDialog(this, message, "归还确认", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                bookDAO.returnBook(bookId, currentUser.getId());

                // 归还成功后刷新
                refreshTable(null);
                txtSearch.setText(""); // 清空搜索框

                JOptionPane.showMessageDialog(this, "归还成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException | BusinessException ex) {
                JOptionPane.showMessageDialog(this, "归还失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}