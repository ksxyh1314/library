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
 * 1. UI 风格与 BorrowBookPanel 保持一致（顶部搜索栏）
 * 2. 保留了底部的统计信息栏
 * 3. ★ 添加应还日期列，显示超期信息
 * 4. ★ 支持罚款支付功能
 * 5. ★ 超期图书必须管理员记录罚款后才能归还
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

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📤 归还图书");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));

        JLabel userInfoLabel = new JLabel("  当前用户: " + currentUser.getUsername() +
                " (ID: " + currentUser.getId() + ")");
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
        titlePanel.add(modeLabel);

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
        JLabel infoLabel = new JLabel("📖 以下为当前未归还的图书，选择后点击【归还选中图书】按钮进行归还（超期图书需管理员记录罚款后才能归还）");
        infoLabel.setForeground(new Color(231, 76, 60));
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
        bookTable = new JTable();
        bookTable.getTableHeader().setReorderingAllowed(false);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookTable.setRowHeight(25);
        bookTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        add(new JScrollPane(bookTable), BorderLayout.CENTER);

        // ============================================================
        // 5. ★ 底部统计信息区域
        // ============================================================
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(245, 245, 245));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        statsLabel = new JLabel("正在加载数据...");
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        statsLabel.setForeground(new Color(204, 102, 0));

        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============================================================
        // 6. 事件监听
        // ============================================================
        btnSearch.addActionListener(e -> refreshTable(txtSearch.getText()));

        btnResetSearch.addActionListener(e -> {
            txtSearch.setText("");
            refreshTable(null);
            bookTable.clearSelection();
        });

        btnReturn.addActionListener(e -> returnBookAction());

        // 回车搜索
        txtSearch.addActionListener(e -> refreshTable(txtSearch.getText()));

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

            // ★ 设置列宽
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
                    bookTable.getColumnModel().getColumn(5).setPreferredWidth(250); // 状态
                }
            }

            // 2. 客户端过滤
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
            bookTable.setRowSorter(sorter);

            if (keyword != null && !keyword.trim().isEmpty()) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + keyword));

                if (bookTable.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(this,
                            "未找到关键词 [" + keyword + "] 的已借图书。",
                            "搜索结果",
                            JOptionPane.INFORMATION_MESSAGE);
                }
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
     * ★ 更新底部统计文字
     */
    private void updateStats() {
        int totalCount = bookTable.getRowCount();
        int overdueCount = 0;
        int pendingFineCount = 0; // 待支付罚款数量
        int needAdminCount = 0;   // 需要管理员处理数量

        // ★ 统计超期和待支付罚款图书
        for (int i = 0; i < totalCount; i++) {
            if (bookTable.getColumnCount() > 5) {
                Object statusObj = bookTable.getValueAt(i, 5);
                if (statusObj != null) {
                    String status = statusObj.toString();
                    if (status.contains("已超期")) {
                        overdueCount++;
                    }
                    if (status.contains("待支付罚款")) {
                        pendingFineCount++;
                    }
                    if (status.contains("请联系管理员")) {
                        needAdminCount++;
                    }
                }
            }
        }

        // ★ 显示统计信息
        String statsText;
        if (needAdminCount > 0) {
            statsText = String.format(
                    "当前未归还图书: %d 本  |  已超期: %d 本  |  需管理员处理: %d 本  |  待支付罚款: %d 本",
                    totalCount, overdueCount, needAdminCount, pendingFineCount
            );
        } else if (pendingFineCount > 0) {
            statsText = String.format(
                    "当前未归还图书: %d 本  |  已超期: %d 本  |  待支付罚款: %d 本",
                    totalCount, overdueCount, pendingFineCount
            );
        } else {
            statsText = String.format(
                    "当前未归还图书: %d 本  |  已超期: %d 本",
                    totalCount, overdueCount
            );
        }
        statsLabel.setText(statsText);

        // ★ 根据状态改变颜色
        if (needAdminCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43)); // 红色 - 需要管理员处理
        } else if (pendingFineCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34)); // 橙色 - 有待支付罚款
        } else if (overdueCount > 0) {
            statsLabel.setForeground(new Color(241, 196, 15)); // 黄色 - 有超期
        } else if (totalCount > 0) {
            statsLabel.setForeground(new Color(52, 152, 219)); // 蓝色 - 有未归还
        } else {
            statsLabel.setForeground(new Color(39, 174, 96)); // 绿色 - 全部已归还
        }
    }

    /**
     * ★ 归还图书动作（支持罚款支付，强制检查管理员是否已记录罚款）
     */
    private void returnBookAction() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "请先选择要归还的图书。",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = bookTable.convertRowIndexToModel(row);
        DefaultTableModel model = (DefaultTableModel) bookTable.getModel();

        // 获取图书信息
        int bookId = (int) model.getValueAt(modelRow, 0);
        String title = (String) model.getValueAt(modelRow, 1);
        String author = (String) model.getValueAt(modelRow, 2);
        String status = model.getValueAt(modelRow, 5).toString(); // 状态列

        try {
            // ★ 1. 检查是否需要管理员处理
            if (status.contains("请联系管理员处理罚款")) {
                JOptionPane.showMessageDialog(this,
                        "该图书已超期，但管理员尚未记录罚款。\n\n" +
                                "请联系管理员在【超期和遗失管理】中记录罚款后，再次尝试归还。\n\n" +
                                "当前状态：" + status,
                        "无法归还",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // ★ 2. 查询借阅记录信息（包括罚款）
            BookDAO.BorrowRecordInfo recordInfo = bookDAO.getBorrowRecordInfo(bookId, currentUser.getId());

            if (recordInfo == null) {
                JOptionPane.showMessageDialog(this,
                        "未找到该图书的借阅记录。",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            double finePayment = 0;

            // ★ 3. 如果有待支付罚款，弹出支付对话框
            if (recordInfo.fineAmount > 0 && !recordInfo.finePaid) {
                // 显示罚款支付对话框
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        String.format(
                                "图书：%s\n" +
                                        "作者：%s\n" +
                                        "借出日期：%s\n\n" +
                                        "⚠️ 该图书已超期，需支付罚款：%.2f 元\n\n" +
                                        "是否确认支付罚款并归还图书？",
                                recordInfo.bookTitle,
                                recordInfo.bookAuthor,
                                recordInfo.borrowTime.toString(),
                                recordInfo.fineAmount
                        ),
                        "支付罚款确认",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (confirm != JOptionPane.YES_OPTION) {
                    return; // 用户取消支付
                }

                finePayment = recordInfo.fineAmount;

            } else {
                // 没有罚款，正常归还确认
                String message = String.format(
                        "确认归还以下图书吗？\n\n书名：%s\n作者：%s\n图书ID：%d",
                        title, author, bookId
                );

                int confirm = JOptionPane.showConfirmDialog(this,
                        message,
                        "归还确认",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            // ★ 4. 执行归还操作（传递罚款金额）
            bookDAO.returnBook(bookId, currentUser.getId(), finePayment);

            // ★ 5. 成功提示
            if (finePayment > 0) {
                JOptionPane.showMessageDialog(this,
                        String.format("归还成功！\n\n图书: %s\n已支付罚款: %.2f 元", title, finePayment),
                        "归还成功",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "图书 [" + title + "] 归还成功！",
                        "归还成功",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            // ★ 6. 刷新表格
            refreshTable(null);
            txtSearch.setText("");

        } catch (DBException | BusinessException ex) {
            JOptionPane.showMessageDialog(this,
                    "归还失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
