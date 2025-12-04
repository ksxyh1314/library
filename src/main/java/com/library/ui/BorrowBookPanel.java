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
 * 借书面板
 * ★ 优化：界面风格与 ReturnBookPanel 保持一致
 */
public class BorrowBookPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable bookTable;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;
    private User currentUser;

    // UI 组件
    private JTextField txtSearch;
    private JButton btnSearch;
    private JButton btnResetSearch;
    private JLabel statsLabel;

    public BorrowBookPanel(User user) {
        this.currentUser = user;
        setLayout(new BorderLayout());

        // ============================================================
        // 1. ★ 顶部标题面板（与 ReturnBookPanel 样式一致）
        // ============================================================
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📚 借阅图书");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));

        JLabel userInfoLabel = new JLabel("  当前用户: " + currentUser.getUsername() +
                " (ID: " + currentUser.getId() + ")");
        userInfoLabel.setForeground(new Color(127, 140, 141));

        titlePanel.add(titleLabel);
        titlePanel.add(userInfoLabel);

        // ============================================================
        // 2. 搜索 + 按钮区域（一行显示）
        // ============================================================
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        txtSearch = new JTextField(20);
        btnSearch = new JButton("🔍 搜索可借图书");
        btnResetSearch = new JButton("↺ 重置");
        JButton btnBorrow = new JButton("📥 借阅选中图书");

        controlPanel.add(new JLabel("书名关键词:"));
        controlPanel.add(txtSearch);
        controlPanel.add(btnSearch);
        controlPanel.add(btnResetSearch);
        controlPanel.add(btnBorrow);

        // ============================================================
        // 3. 提示信息区域
        // ============================================================
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📋 提示：以下为可借阅图书列表，选择后点击【借阅选中图书】按钮进行借阅");
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        // ★ 组合顶部容器
        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(titlePanel, BorderLayout.NORTH);
        northContainer.add(controlPanel, BorderLayout.CENTER);
        northContainer.add(infoPanel, BorderLayout.SOUTH);
        add(northContainer, BorderLayout.NORTH);

        // ============================================================
        // 4. 中间表格区域
        // ============================================================
        bookTable = new JTable() {
            // ★★★ 禁用自动滚动到选中行
            @Override
            public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
                super.changeSelection(rowIndex, columnIndex, toggle, extend);
            }
        };

        bookTable.getTableHeader().setReorderingAllowed(false);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookTable.setRowHeight(25);
        bookTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));

        // ★★★ 禁用自动滚动
        bookTable.setAutoscrolls(false);

        refreshTable(null);

        JScrollPane scrollPane = new JScrollPane(bookTable);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // ============================================================
        // 5. ★ 底部统计信息区域（与 ReturnBookPanel 样式一致）
        // ============================================================
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(245, 245, 245));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        statsLabel = new JLabel("正在加载数据...");
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));

        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============================================================
        // 6. 事件监听器
        // ============================================================
        btnSearch.addActionListener(e -> performSearch());
        txtSearch.addActionListener(e -> performSearch());

        btnResetSearch.addActionListener(e -> {
            txtSearch.setText("");
            bookTable.clearSelection();
            refreshTable(null);
        });

        btnBorrow.addActionListener(e -> borrowBookAction());

        updateStats();
    }

    /**
     * 执行搜索
     */
    private void performSearch() {
        String keyword = txtSearch.getText().trim();
        refreshTable(keyword.isEmpty() ? null : keyword);
    }

    /**
     * 刷新表格数据并更新底部统计
     */
    private void refreshTable(String keyword) {
        model = bookDAO.getBookModel(keyword, true);
        bookTable.setModel(model);

        // 调整列宽
        if (bookTable.getColumnCount() > 0) {
            // 图书ID
            bookTable.getColumnModel().getColumn(0).setPreferredWidth(80);
            bookTable.getColumnModel().getColumn(0).setMinWidth(60);

            // 书名
            bookTable.getColumnModel().getColumn(1).setPreferredWidth(200);
            bookTable.getColumnModel().getColumn(1).setMinWidth(150);

            // 作者
            bookTable.getColumnModel().getColumn(2).setPreferredWidth(120);
            bookTable.getColumnModel().getColumn(2).setMinWidth(100);

            // 状态
            bookTable.getColumnModel().getColumn(3).setPreferredWidth(120);
            bookTable.getColumnModel().getColumn(3).setMinWidth(80);
        }

        // ★★★ 关键：使用 AUTO_RESIZE_SUBSEQUENT_COLUMNS 铺满界面
        bookTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // 左对齐
        javax.swing.table.DefaultTableCellRenderer leftRenderer = new javax.swing.table.DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

        for (int i = 0; i < bookTable.getColumnCount(); i++) {
            bookTable.getColumnModel().getColumn(i).setCellRenderer(leftRenderer);
        }

        sorter = new TableRowSorter<>(model);
        bookTable.setRowSorter(sorter);

        // 搜索结果为空的提示
        if (model.getRowCount() == 0 && keyword != null && !keyword.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "未找到关键词 [" + keyword + "] 的可借阅图书。",
                    "搜索结果", JOptionPane.INFORMATION_MESSAGE);
        }

        updateStats();
    }

    /**
     * 更新底部统计信息
     */
    private void updateStats() {
        if (statsLabel == null || bookTable == null) {
            return;
        }

        int count = bookTable.getRowCount();
        String statsText = String.format("当前可借阅图书数量: %d 本", count);
        statsLabel.setText(statsText);

        if (count == 0) {
            statsLabel.setForeground(new Color(192, 57, 43)); // 红色
        } else if (count < 10) {
            statsLabel.setForeground(new Color(230, 126, 34)); // 橙色
        } else {
            statsLabel.setForeground(new Color(39, 174, 96)); // 绿色
        }
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

        // 转换为模型索引
        int modelRow = bookTable.convertRowIndexToModel(row);

        // 获取选中行的数据
        int bookId = (int) bookTable.getModel().getValueAt(modelRow, 0);
        String title = (String) bookTable.getModel().getValueAt(modelRow, 1);
        String author = (String) bookTable.getModel().getValueAt(modelRow, 2);
        String status = (String) bookTable.getModel().getValueAt(modelRow, 3);

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
