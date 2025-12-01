package com.library.ui;

import com.library.config.SystemConfig;
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
 * 3. ★ 添加应还日期列，显示超期信息
 * 4. ★ 添加标题样式，与 BorrowBookPanel 保持一致
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
        // 1. ★ 顶部标题面板（与 BorrowBookPanel 样式一致）
        // ============================================================
        // 在 ReturnBookPanel 构造函数中
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📤 归还图书");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));

        JLabel userInfoLabel = new JLabel("  当前用户: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");
        userInfoLabel.setForeground(new Color(127, 140, 141));

// ★ 添加模式提示
        JLabel modeLabel = new JLabel("  |  " + SystemConfig.getModeDescription());
        if (SystemConfig.IS_TEST_MODE) {
            modeLabel.setForeground(new Color(231, 76, 60)); // 红色 - 测试模式
        } else {
            modeLabel.setForeground(new Color(39, 174, 96)); // 绿色 - 生产模式
        }
        modeLabel.setFont(new Font("微软雅黑", Font.BOLD, 11));

        titlePanel.add(titleLabel);
        titlePanel.add(userInfoLabel);
        titlePanel.add(modeLabel); // ← 添加模式提示


        // ============================================================
        // 2. 搜索 + 按钮区域
        // ============================================================
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        txtSearch = new JTextField(20);
        btnSearch = new JButton("🔍 搜索已借图书");
        btnResetSearch = new JButton("↺ 重置");
        JButton btnReturn = new JButton("📤 归还选中图书");

        controlPanel.add(new JLabel("书名关键词:"));
        controlPanel.add(txtSearch);
        controlPanel.add(btnSearch);
        controlPanel.add(btnResetSearch);
        controlPanel.add(btnReturn);

        // ============================================================
        // 3. 提示信息区域
        // ============================================================
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📖 以下为当前未归还的图书，选择后点击【归还选中图书】按钮进行归还");
        infoLabel.setForeground(new Color(231, 76, 60)); // 红色提示，区分借书界面
        infoPanel.add(infoLabel);

        // ★ 组合顶部容器（标题 + 控制按钮 + 提示信息）
        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(titlePanel, BorderLayout.NORTH);      // ← 标题行
        northContainer.add(controlPanel, BorderLayout.CENTER);   // ← 搜索和按钮行
        northContainer.add(infoPanel, BorderLayout.SOUTH);       // ← 提示信息行
        add(northContainer, BorderLayout.NORTH);

        // ============================================================
        // 4. 中间表格区域
        // ============================================================
        bookTable = new JTable();
        bookTable.getTableHeader().setReorderingAllowed(false);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(bookTable), BorderLayout.CENTER);

        // ============================================================
        // 5. ★ 底部统计信息区域
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
        // 6. 事件监听
        // ============================================================
        btnSearch.addActionListener(e -> refreshTable(txtSearch.getText()));

        btnResetSearch.addActionListener(e -> {
            txtSearch.setText("");
            refreshTable(null);
            bookTable.clearSelection(); // 取消选中
        });

        btnReturn.addActionListener(e -> returnBookAction());

        // 初始化加载数据
        refreshTable(null);
    }

    /**
     * 刷新表格数据并更新底部统计
     */
    private void refreshTable(String keyword) {
        try {
            // 1. 获取数据模型
            DefaultTableModel model = bookDAO.getCurrentBorrowedBooksModel(currentUser.getId());
            bookTable.setModel(model);

            // ★ 设置列宽（根据新的列结构调整）
            if (bookTable.getColumnCount() > 0) {
                bookTable.getColumnModel().getColumn(0).setPreferredWidth(80);  // 图书ID
                bookTable.getColumnModel().getColumn(1).setPreferredWidth(200); // 书名
                bookTable.getColumnModel().getColumn(2).setPreferredWidth(120); // 作者
                if (bookTable.getColumnCount() > 3) {
                    bookTable.getColumnModel().getColumn(3).setPreferredWidth(150); // 借出日期
                }
                if (bookTable.getColumnCount() > 4) {
                    bookTable.getColumnModel().getColumn(4).setPreferredWidth(150); // 应还日期
                }
                if (bookTable.getColumnCount() > 5) {
                    bookTable.getColumnModel().getColumn(5).setPreferredWidth(180); // 状态
                }
            }


            // 2. 客户端过滤 (实现本地搜索)
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
            bookTable.setRowSorter(sorter);

            if (keyword != null && !keyword.trim().isEmpty()) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + keyword));

                // ✅ 添加搜索无结果的提示弹窗
                if (bookTable.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(this,
                            "未找到关键词 [" + keyword + "] 的已借图书。",
                            "搜索结果",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                bookTable.setRowSorter(null);
            }

            // 3. ★ 更新底部统计数据
            updateStats();

        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "加载记录失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ★ 更新底部统计文字（显示未归还和超期信息）
     */
    private void updateStats() {
        int totalCount = bookTable.getRowCount(); // 当前显示的行数
        int overdueCount = 0; // 超期数量

        // ★ 统计超期图书（假设第5列是状态列，包含"已超期"字样）
        for (int i = 0; i < totalCount; i++) {
            if (bookTable.getColumnCount() > 4) {
                Object statusObj = bookTable.getValueAt(i, 4);
                if (statusObj != null) {
                    String status = statusObj.toString();
                    if (status.contains("已超期")) {
                        overdueCount++;
                    }
                }
            }
        }

        // ★ 显示统计信息
        String statsText = String.format(
                "当前未归还图书: %d 本  |  已超期: %d 本",
                totalCount, overdueCount
        );
        statsLabel.setText(statsText);

        // ★ 根据超期情况改变颜色
        if (overdueCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43)); // 红色 - 有超期
        } else if (totalCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34)); // 橙色 - 有未归还
        } else {
            statsLabel.setForeground(new Color(39, 174, 96)); // 绿色 - 全部已归还
        }
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

        int modelRow = bookTable.convertRowIndexToModel(row);
        DefaultTableModel model = (DefaultTableModel) bookTable.getModel();

        // 获取图书信息
        int bookId = (int) model.getValueAt(modelRow, 0);      // 第0列：图书ID
        String title = (String) model.getValueAt(modelRow, 1);  // 第1列：书名
        String author = (String) model.getValueAt(modelRow, 2); // 第2列：作者

        // 检查是否超期（第5列是状态）
        String status = "";
        if (model.getColumnCount() > 5) {
            Object statusObj = model.getValueAt(modelRow, 5);
            status = statusObj != null ? statusObj.toString() : "";
        }

        // ★★★ 关键修改：如果图书超期，阻止归还 ★★★
        if (status.contains("已超期")) {
            // 提取超期天数
            String daysStr = status.replaceAll("[^0-9]", "");
            int overdueDays = 0;
            try {
                overdueDays = Integer.parseInt(daysStr);
            } catch (NumberFormatException e) {
                overdueDays = 0;
            }

            // 显示超期提示，不允许直接归还
            String overdueMessage = String.format(
                    "⚠ 该图书已超期 %d 天，无法直接归还！\n\n" +
                            "━━━━━━━━━━━━━━━━━━━━━━\n" +
                            "书名：%s\n" +
                            "作者：%s\n" +
                            "图书ID：%d\n" +
                            "━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                            "\u0001F4E2 请联系管理员处理超期罚款后才能归还图书。\n" +
                            "管理员可在【超期和遗失管理】中处理罚款。",
                    overdueDays, title, author, bookId
            );

            JOptionPane.showMessageDialog(this,
                    overdueMessage,
                    "超期图书无法归还",
                    JOptionPane.WARNING_MESSAGE);
            return; // ← 阻止归还操作
        }

        // ★ 正常图书的确认对话框（未超期才能执行到这里）
        String message = String.format(
                "确认归还以下图书吗？\n\n书名：%s\n作者：%s\n图书ID：%d",
                title, author, bookId
        );

        int confirm = JOptionPane.showConfirmDialog(this, message, "归还确认", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // 只有未超期的图书才能通过这里归还
                bookDAO.returnBook(bookId, currentUser.getId());

                // 归还成功后刷新
                refreshTable(null);
                txtSearch.setText("");

                JOptionPane.showMessageDialog(this, "归还成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException | BusinessException ex) {
                JOptionPane.showMessageDialog(this, "归还失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


}
