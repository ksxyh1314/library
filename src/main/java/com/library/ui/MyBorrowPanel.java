package com.library.ui;

import com.library.dao.BookDAO;
import com.library.entity.User;
import com.library.exception.DBException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 普通用户查看借阅历史记录的面板（增强版 - 带实际功能）
 * ★ 改进：添加图标、筛选功能、导出功能和底部统计
 * ★ 新增：未归还显示"应归还日期"，已归还显示"归还日期"
 * ★ 优化：所有按钮放在同一行
 */
public class MyBorrowPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable recordTable;
    private User currentUser;
    private JLabel statsLabel;
    private JComboBox<String> statusFilter;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;

    public MyBorrowPanel(User user) {
        this.currentUser = user;
        setLayout(new BorderLayout());

        // --- 1. 顶部操作面板 ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // 标题面板
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📋 我的借阅记录");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        JLabel userInfoLabel = new JLabel("  当前用户: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");
        userInfoLabel.setForeground(new Color(127, 140, 141));
        titlePanel.add(titleLabel);
        titlePanel.add(userInfoLabel);

        // ★ 筛选和操作按钮放在同一行
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));

        // 筛选部分
        controlPanel.add(new JLabel("筛选状态:"));
        statusFilter = new JComboBox<>(new String[]{
                "全部记录",
                "未归还",
                "已超期",
                "已归还",
                "已遗失"
        });
        statusFilter.setSelectedIndex(0);
        controlPanel.add(statusFilter);

        JButton btnResetFilter = new JButton("↺ 重置");
        controlPanel.add(btnResetFilter);

        // ★ 添加分隔符（可选）
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(2, 25));
        controlPanel.add(separator);

        // 操作按钮部分
        JButton btnRefresh = new JButton("🔄 刷新记录");
        JButton btnExport = new JButton("📤 导出记录");
        controlPanel.add(btnRefresh);
        controlPanel.add(btnExport);

        topPanel.add(titlePanel, BorderLayout.NORTH);
        topPanel.add(controlPanel, BorderLayout.CENTER);

        // --- 2. 提示信息 ---
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📖 以下为您的所有借阅历史记录，包括已归还和未归还的图书");
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // --- 3. 中间表格 ---
        recordTable = new JTable();
        recordTable.getTableHeader().setReorderingAllowed(false);
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

        // ============ 事件监听 ============

        // 刷新按钮
        btnRefresh.addActionListener(e -> {
            refreshTable();
            JOptionPane.showMessageDialog(this, "记录已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        // 重置按钮
        btnResetFilter.addActionListener(e -> {
            statusFilter.setSelectedIndex(0);
            applyFilter();
            recordTable.clearSelection(); // 添加这行
        });

        // 筛选功能监听
        statusFilter.addActionListener(e -> applyFilter());

        // 导出功能
        btnExport.addActionListener(e -> exportToCSV());

        // ★ 初始化时更新统计信息（一登录就显示）
        updateStats();
    }

    /**
     * 刷新表格数据
     */
    private void refreshTable() {
        try {
            model = bookDAO.getMyBorrowRecordsModel(currentUser.getId());
            recordTable.setModel(model);

            // 设置列宽
            if (recordTable.getColumnCount() > 0) {
                recordTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
                recordTable.getColumnModel().getColumn(1).setPreferredWidth(200); // 书名
                recordTable.getColumnModel().getColumn(2).setPreferredWidth(150); // 借出日期
                recordTable.getColumnModel().getColumn(3).setPreferredWidth(150); // ★ 应归还日期/归还日期
                recordTable.getColumnModel().getColumn(4).setPreferredWidth(100); // 是否归还
                recordTable.getColumnModel().getColumn(5).setPreferredWidth(200); // 状态
            }

            // 设置排序器
            sorter = new TableRowSorter<>(model);
            recordTable.setRowSorter(sorter);

            // 应用当前筛选
            applyFilter();

        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "加载记录失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ★ 应用筛选条件
     */
    private void applyFilter() {
        if (sorter == null || statusFilter == null) {
            return;
        }

        String selected = (String) statusFilter.getSelectedItem();

        if ("全部记录".equals(selected)) {
            sorter.setRowFilter(null);
        } else if ("未归还".equals(selected)) {
            // 第5列（索引4）="未归还"
            RowFilter<DefaultTableModel, Object> filter = RowFilter.regexFilter("未归还", 4);
            sorter.setRowFilter(filter);
        } else if ("已超期".equals(selected)) {
            // 第6列（索引5）包含"已超期"
            RowFilter<DefaultTableModel, Object> filter = RowFilter.regexFilter("已超期", 5);
            sorter.setRowFilter(filter);
        } else if ("已归还".equals(selected)) {
            // 第5列（索引4）="已归还"
            RowFilter<DefaultTableModel, Object> filter = RowFilter.regexFilter("已归还", 4);
            sorter.setRowFilter(filter);
        } else if ("已遗失".equals(selected)) {
            // 第5列（索引4）="遗失"
            RowFilter<DefaultTableModel, Object> filter = RowFilter.regexFilter("遗失", 4);
            sorter.setRowFilter(filter);
        }

        // 更新统计信息
        updateStats();
    }

    /**
     * ★ 更新底部统计信息（添加已归还统计）
     */
    private void updateStats() {
        if (statsLabel == null || recordTable == null || model == null) {
            return;
        }

        int totalCount = recordTable.getRowCount(); // 筛选后的行数
        int unreturnedCount = 0;  // 未归还
        int overdueCount = 0;     // 已超期
        int returnedCount = 0;    // ★ 已归还
        int lostCount = 0;        // 已遗失

        // 统计筛选后的数据
        for (int i = 0; i < totalCount; i++) {
            String returnStatus = (String) recordTable.getValueAt(i, 4);
            String statusInfo = (String) recordTable.getValueAt(i, 5);

            if ("未归还".equals(returnStatus)) {
                unreturnedCount++;  // 统计"未归还"
                if (statusInfo.contains("已超期")) {
                    overdueCount++;  // 统计"已超期"
                }
            } else if ("已归还".equals(returnStatus)) {
                returnedCount++;  // ★ 统计"已归还"
            } else if ("遗失".equals(returnStatus)) {
                lostCount++;  // 统计"已遗失"
            }
        }

        // ★ 显示统计信息（添加已归还）
        String statsText = String.format(
                "当前显示: %d 条  |  未归还: %d 本  |  已超期: %d 本  |  已归还: %d 本  |  已遗失: %d 本",
                totalCount, unreturnedCount, overdueCount, returnedCount, lostCount
        );
        statsLabel.setText(statsText);

        // ★ 颜色优先级：超期 > 遗失 > 未归还 > 正常
        if (overdueCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43)); // 红色 - 有超期
        } else if (lostCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34)); // 橙色 - 有遗失
        } else if (unreturnedCount > 0) {
            statsLabel.setForeground(new Color(52, 152, 219)); // 蓝色 - 有未归还
        } else {
            statsLabel.setForeground(new Color(39, 174, 96)); // 绿色 - 全部已归还
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
        fileChooser.setSelectedFile(new File("我的借阅记录_" + currentUser.getUsername() + "_" + System.currentTimeMillis() + ".csv"));

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

                // 写入数据
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
