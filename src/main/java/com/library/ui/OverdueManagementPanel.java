package com.library.ui;

import com.library.dao.BookDAO;
import com.library.exception.BusinessException;
import com.library.exception.DBException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 超期和遗失管理面板 - 管理员专用
 * 功能：处理超期罚款、遗失罚款、新书替换
 * ★ 新增：精准搜索功能（用户名/书名）、完整统计信息
 */
public class OverdueManagementPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable recordTable;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;
    private JComboBox<String> cmbSearchType;
    private JComboBox<String> cmbStatusFilter;
    private JTextField txtSearch;
    private JLabel statsLabel;

    public OverdueManagementPanel() {
        setLayout(new BorderLayout());

        // --- 1. 顶部操作面板 ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // 标题面板
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("⏰ 超期和遗失管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // ★ 搜索和筛选面板（单独一行）
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // 搜索类型选择
        cmbSearchType = new JComboBox<>(new String[]{"用户名", "书名"});
        searchPanel.add(cmbSearchType);

        // 搜索输入框
        txtSearch = new JTextField(15);
        searchPanel.add(txtSearch);

        // 搜索按钮
        JButton btnSearch = new JButton("🔍 搜索");
        searchPanel.add(btnSearch);

        // 分隔符
        searchPanel.add(new JLabel("  |  "));

        // 状态筛选
        searchPanel.add(new JLabel("筛选状态:"));
        cmbStatusFilter = new JComboBox<>(new String[]{
                "全部记录",
                "未归还",
                "已超期",
                "已归还",
                "已遗失"
        });
        cmbStatusFilter.setSelectedIndex(0);
        searchPanel.add(cmbStatusFilter);

        // 分隔符
        searchPanel.add(new JLabel("  |  "));

        // ★ 统一的重置按钮（重置搜索和筛选）
        JButton btnReset = new JButton("↺ 重置");
        searchPanel.add(btnReset);

        // ★ 操作按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton btnRefresh = new JButton("🔄 刷新数据");
        JButton btnOverdueFine = new JButton("💰 超期罚款");
        JButton btnHandleLoss = new JButton("⌛ 遗失处理");
        JButton btnExport = new JButton("📤 导出记录");

        buttonPanel.add(btnRefresh);
        buttonPanel.add(btnOverdueFine);
        buttonPanel.add(btnHandleLoss);
        buttonPanel.add(btnExport);

        // 组合控制面板
        JPanel controlsContainer = new JPanel(new BorderLayout());
        controlsContainer.add(searchPanel, BorderLayout.NORTH);
        controlsContainer.add(buttonPanel, BorderLayout.CENTER);

        topPanel.add(titlePanel, BorderLayout.NORTH);
        topPanel.add(controlsContainer, BorderLayout.CENTER);

        // --- 2. 提示信息 ---
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📋 提示：可按用户名或书名精准搜索，选择记录后可进行超期罚款或遗失处理");
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // --- 3. 中间表格 ---
        recordTable = new JTable();
        recordTable.getTableHeader().setReorderingAllowed(false);
        recordTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshTable();
        add(new JScrollPane(recordTable), BorderLayout.CENTER);

        // --- 4. 底部统计信息 ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(240, 240, 240));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ★ 初始化时更新统计信息
        updateStats();

        // ============ 事件监听 ============

        // ★ 搜索功能
        btnSearch.addActionListener(e -> performSearch());
        txtSearch.addActionListener(e -> performSearch()); // 回车搜索

        // ★ 统一的重置按钮（重置搜索框、搜索类型、状态筛选）
        btnReset.addActionListener(e -> {
            txtSearch.setText("");
            cmbSearchType.setSelectedIndex(0);
            cmbStatusFilter.setSelectedIndex(0);
            performSearch();
        });

        // ★ 筛选功能
        cmbStatusFilter.addActionListener(e -> performSearch());

        // 刷新按钮
        btnRefresh.addActionListener(e -> {
            refreshTable();
            JOptionPane.showMessageDialog(this, "数据已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        // 超期罚款按钮
        btnOverdueFine.addActionListener(e -> handleOverdueFine());

        // 处理遗失按钮
        btnHandleLoss.addActionListener(e -> handleBookLoss());

        // 导出按钮
        btnExport.addActionListener(e -> exportToCSV());
    }

    /**
     * 刷新表格数据 - 显示所有借阅记录
     */
    private void refreshTable() {
        try {
            model = bookDAO.getAllBorrowRecordsModel();
            recordTable.setModel(model);

            // 设置列宽
            if (recordTable.getColumnCount() > 0) {
                recordTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // 记录ID
                recordTable.getColumnModel().getColumn(1).setPreferredWidth(60);  // 图书ID
                recordTable.getColumnModel().getColumn(2).setPreferredWidth(180); // 图书名称
                recordTable.getColumnModel().getColumn(3).setPreferredWidth(60);  // 用户ID
                recordTable.getColumnModel().getColumn(4).setPreferredWidth(100); // 用户名
                recordTable.getColumnModel().getColumn(5).setPreferredWidth(150); // 借出日期
                recordTable.getColumnModel().getColumn(6).setPreferredWidth(150); // 应还日期
                recordTable.getColumnModel().getColumn(7).setPreferredWidth(80);  // 是否归还
                recordTable.getColumnModel().getColumn(8).setPreferredWidth(200); // 状态/处理结果
            }

            // ★ 设置排序器 (用于筛选和搜索)
            sorter = new TableRowSorter<>(model);
            recordTable.setRowSorter(sorter);

            // 清空搜索框和筛选
            if (txtSearch != null) {
                txtSearch.setText("");
            }
            if (cmbSearchType != null) {
                cmbSearchType.setSelectedIndex(0);
            }
            if (cmbStatusFilter != null) {
                cmbStatusFilter.setSelectedIndex(0);
            }

            updateStats();

        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "加载数据失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ★ 执行精准搜索和筛选（组合功能）
     */
    private void performSearch() {
        if (sorter == null) {
            return;
        }

        String searchText = txtSearch.getText().trim();
        String searchType = (String) cmbSearchType.getSelectedItem();
        String selectedStatus = (String) cmbStatusFilter.getSelectedItem();

        // 组合过滤条件
        RowFilter<DefaultTableModel, Object> combinedFilter = null;

        // 1. ★ 精准搜索过滤（用户名或书名）
        RowFilter<DefaultTableModel, Object> searchFilter = null;
        if (!searchText.isEmpty()) {
            if ("用户名".equals(searchType)) {
                // 精准匹配用户名（第5列，索引4）
                searchFilter = RowFilter.regexFilter("(?i)^" + Pattern.quote(searchText) + "$", 4);
            } else if ("书名".equals(searchType)) {
                // 精准匹配书名（第3列，索引2）
                searchFilter = RowFilter.regexFilter("(?i)^" + Pattern.quote(searchText) + "$", 2);
            }
        }

        // 2. 状态过滤
        RowFilter<DefaultTableModel, Object> statusFilter = null;
        if (!"全部记录".equals(selectedStatus)) {
            if ("未归还".equals(selectedStatus)) {
                statusFilter = RowFilter.regexFilter("未归还", 7);
            } else if ("已超期".equals(selectedStatus)) {
                statusFilter = RowFilter.regexFilter("已超期", 8);
            } else if ("已归还".equals(selectedStatus)) {
                statusFilter = RowFilter.regexFilter("已归还", 7);
            } else if ("已遗失".equals(selectedStatus)) {
                statusFilter = RowFilter.regexFilter("遗失", 7);
            }
        }

        // 3. 组合过滤器
        if (searchFilter != null && statusFilter != null) {
            combinedFilter = RowFilter.andFilter(java.util.Arrays.asList(searchFilter, statusFilter));
        } else if (searchFilter != null) {
            combinedFilter = searchFilter;
        } else if (statusFilter != null) {
            combinedFilter = statusFilter;
        }

        sorter.setRowFilter(combinedFilter);
        updateStats();

        // 提示搜索结果
        if (!searchText.isEmpty() && recordTable.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "未找到" + searchType + "为 [" + searchText + "] 的记录。\n\n提示：请输入完整的" + searchType + "（精准匹配）",
                    "搜索结果",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * ★ 更新底部统计信息（包含已归还）
     */
    private void updateStats() {
        if (statsLabel == null || recordTable == null) {
            return;
        }

        int totalCount = recordTable.getRowCount(); // 筛选后的行数
        int unreturnedCount = 0;
        int overdueCount = 0;
        int returnedCount = 0;
        int lostCount = 0;

        // 统计筛选后的数据
        for (int i = 0; i < totalCount; i++) {
            String returnStatus = (String) recordTable.getValueAt(i, 7);
            String statusInfo = (String) recordTable.getValueAt(i, 8);

            if ("未归还".equals(returnStatus)) {
                unreturnedCount++;
                if (statusInfo != null && statusInfo.contains("已超期")) {
                    overdueCount++;
                }
            } else if ("已归还".equals(returnStatus)) {
                returnedCount++;
            } else if ("遗失".equals(returnStatus)) {
                lostCount++;
            }
        }

        // ★ 完整的统计信息
        String statsText = String.format(
                "当前显示: %d 条  |  未归还: %d 本  |  已超期: %d 本  |  已归还: %d 本  |  已遗失: %d 本",
                totalCount, unreturnedCount, overdueCount, returnedCount, lostCount
        );
        statsLabel.setText(statsText);

        // 根据状态设置颜色
        if (overdueCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43)); // 深红色
        } else if (lostCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34)); // 橙色
        } else if (unreturnedCount > 0) {
            statsLabel.setForeground(new Color(41, 128, 185)); // 蓝色
        } else {
            statsLabel.setForeground(new Color(39, 174, 96)); // 绿色
        }
    }

    /**
     * 处理超期罚款
     */
    /**
     * 处理超期罚款
     */
    private void handleOverdueFine() {
        int row = recordTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "请先选择要处理的借阅记录。",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 获取记录信息
        int borrowId = (int) recordTable.getValueAt(row, 0);
        String bookTitle = (String) recordTable.getValueAt(row, 2);
        String username = (String) recordTable.getValueAt(row, 4);
        String returnStatus = (String) recordTable.getValueAt(row, 7);
        String statusInfo = (String) recordTable.getValueAt(row, 8);

        // ★ 1. 判断是否已归还
        if ("已归还".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已归还，无法处理超期罚款。\n\n图书：%s\n借阅人：%s\n状态：%s",
                            bookTitle, username, statusInfo),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 2. 判断是否已遗失
        if ("遗失".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已标记为遗失，无法处理超期罚款。\n\n图书：%s\n借阅人：%s\n状态：%s",
                            bookTitle, username, statusInfo),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 3. 判断是否未归还
        if (!"未归还".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该记录状态异常，无法处理。\n\n图书：%s\n借阅人：%s\n当前状态：%s",
                            bookTitle, username, returnStatus),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 4. 判断是否超期
        if (statusInfo == null || !statusInfo.contains("已超期")) {
            JOptionPane.showMessageDialog(this,
                    String.format("该书籍没有超期，无法处理超期罚款。\n\n图书：%s\n借阅人：%s\n当前状态：%s",
                            bookTitle, username, statusInfo != null ? statusInfo : "借阅中"),
                    "操作失败",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 输入罚款金额
        String input = JOptionPane.showInputDialog(this,
                String.format("请输入超期罚款金额（元）：\n\n图书：%s\n借阅人：%s\n状态：%s",
                        bookTitle, username, statusInfo),
                "超期罚款处理",
                JOptionPane.QUESTION_MESSAGE);

        if (input == null || input.trim().isEmpty()) {
            return; // 用户取消
        }

        try {
            double fineAmount = Double.parseDouble(input.trim());
            if (fineAmount <= 0) {
                JOptionPane.showMessageDialog(this,
                        "罚款金额必须大于0！",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 确认处理
            int confirm = JOptionPane.showConfirmDialog(this,
                    String.format("确认处理超期罚款吗？\n\n图书：%s\n借阅人：%s\n罚款金额：%.2f 元",
                            bookTitle, username, fineAmount),
                    "确认处理",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                bookDAO.recordOverdueFine(borrowId, fineAmount);
                JOptionPane.showMessageDialog(this,
                        String.format("超期罚款处理成功！\n罚款金额：%.2f 元", fineAmount),
                        "处理成功",
                        JOptionPane.INFORMATION_MESSAGE);
                refreshTable();
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "请输入有效的金额数字！",
                    "输入错误",
                    JOptionPane.ERROR_MESSAGE);
        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "处理失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }


    /**
     * 处理图书遗失（罚款或新书替换）
     */
    /**
     * 处理图书遗失（罚款或新书替换）
     */
    private void handleBookLoss() {
        int row = recordTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "请先选择要处理的借阅记录。",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 获取记录信息
        int bookId = (int) recordTable.getValueAt(row, 1);
        String bookTitle = (String) recordTable.getValueAt(row, 2);
        String username = (String) recordTable.getValueAt(row, 4);
        String returnStatus = (String) recordTable.getValueAt(row, 7);
        String statusInfo = (String) recordTable.getValueAt(row, 8);

        // ★ 1. 判断是否已归还
        if ("已归还".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已归还，无法处理遗失。\n\n图书：%s\n借阅人：%s\n状态：%s",
                            bookTitle, username, statusInfo),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 2. 判断是否已遗失
        if ("遗失".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已标记为遗失，无法重复处理。\n\n图书：%s\n借阅人：%s\n状态：%s",
                            bookTitle, username, statusInfo),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 3. 判断是否未归还
        if (!"未归还".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该记录状态异常，无法处理。\n\n图书：%s\n借阅人：%s\n当前状态：%s",
                            bookTitle, username, returnStatus),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 弹出遗失处理对话框
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        LossResolutionDialog dialog = new LossResolutionDialog(parentFrame, bookId);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return; // 用户取消
        }

        String resolutionType = dialog.getResolutionType();
        double amount = dialog.getAmount();

        // 确认处理
        String message;
        if ("Replacement".equals(resolutionType)) {
            message = String.format(
                    "确认处理图书遗失吗？\n\n图书：%s (ID: %d)\n借阅人：%s\n处理方式：新书替换\n\n" +
                            "操作说明：\n• 旧书将被标记为'已删除'\n• 新书将自动上架（可借阅）",
                    bookTitle, bookId, username
            );
        } else {
            message = String.format(
                    "确认处理图书遗失吗？\n\n图书：%s (ID: %d)\n借阅人：%s\n处理方式：遗失罚款\n罚款金额：%.2f 元\n\n" +
                            "操作说明：\n• 图书将被标记为'遗失'\n• 借阅记录将自动结清",
                    bookTitle, bookId, username, amount
            );
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                message,
                "确认处理",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                bookDAO.handleBookLost(bookId, resolutionType, amount);

                String successMsg;
                if ("Replacement".equals(resolutionType)) {
                    successMsg = "新书替换处理成功！\n\n旧书已删除，新书已自动上架。";
                } else {
                    successMsg = String.format("遗失罚款处理成功！\n\n罚款金额：%.2f 元\n图书已标记为遗失。", amount);
                }

                JOptionPane.showMessageDialog(this,
                        successMsg,
                        "处理成功",
                        JOptionPane.INFORMATION_MESSAGE);
                refreshTable();

            } catch (DBException | BusinessException ex) {
                JOptionPane.showMessageDialog(this,
                        "处理失败: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    /**
     * 导出数据到CSV文件
     */
    private void exportToCSV() {
        if (recordTable.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "没有数据可以导出！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存CSV文件");
        fileChooser.setSelectedFile(new File("超期遗失记录_" + System.currentTimeMillis() + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            try (FileWriter writer = new FileWriter(fileToSave)) {
                // 写入BOM（UTF-8标记，让Excel正确识别中文）
                writer.write('\ufeff');

                // 写入表头
                for (int i = 0; i < recordTable.getColumnCount(); i++) {
                    writer.append(recordTable.getColumnName(i));
                    if (i < recordTable.getColumnCount() - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");

                // 写入数据（使用视图中的行，考虑筛选）
                for (int i = 0; i < recordTable.getRowCount(); i++) {
                    for (int j = 0; j < recordTable.getColumnCount(); j++) {
                        Object value = recordTable.getValueAt(i, j);
                        String cellValue = value != null ? value.toString() : "";
                        if (cellValue.contains(",")) {
                            cellValue = "\"" + cellValue + "\"";
                        }
                        writer.append(cellValue);
                        if (j < recordTable.getColumnCount() - 1) {
                            writer.append(",");
                        }
                    }
                    writer.append("\n");
                }

                JOptionPane.showMessageDialog(this,
                        "数据已成功导出到：\n" + fileToSave.getAbsolutePath() +
                                "\n\n共导出 " + recordTable.getRowCount() + " 条记录",
                        "导出成功",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "导出失败: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
