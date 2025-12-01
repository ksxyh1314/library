package com.library.ui;

import com.library.dao.BookDAO;
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
 * 读者借阅记录面板 - 管理员查看所有用户的借阅历史
 * 功能：显示所有借阅记录、按用户名搜索、导出记录
 * ★ 修改：显示归还时间而不是应还时间
 */
public class AdminStatusPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable table;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtSearchUser;
    private JComboBox<String> cmbStatusFilter;
    private JLabel statsLabel;

    public AdminStatusPanel() {
        setLayout(new BorderLayout());

        // --- 1. 顶部面板 ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // 标题
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📚 借阅记录查询");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // ★ 搜索和筛选面板（单独一行）
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // 添加"用户名全称:"标签
        searchPanel.add(new JLabel("用户名全称:"));

        // 用户名搜索框
        txtSearchUser = new JTextField(15);
        searchPanel.add(txtSearchUser);

        JButton btnSearch = new JButton("🔍 搜索用户");
        searchPanel.add(btnSearch);

        // 分隔符
        searchPanel.add(new JLabel("  |  "));

        // ★ 状态筛选（修改选项文字）
        searchPanel.add(new JLabel("筛选状态:"));
        cmbStatusFilter = new JComboBox<>(new String[]{"全部记录", "未归还", "已归还", "已遗失"});
        searchPanel.add(cmbStatusFilter);

        // 分隔符
        searchPanel.add(new JLabel("  |  "));

        // ★ 重置按钮（与超期遗失面板样式一致）
        JButton btnReset = new JButton("↺ 重置");
        searchPanel.add(btnReset);

        // ★ 操作按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton btnRefresh = new JButton("🔄 刷新数据");
        JButton btnExport = new JButton("📤 导出记录");

        buttonPanel.add(btnRefresh);
        buttonPanel.add(btnExport);

        // 组合控制面板
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(titlePanel, BorderLayout.NORTH);
        controlPanel.add(searchPanel, BorderLayout.CENTER);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);

        topPanel.add(controlPanel, BorderLayout.CENTER);

        // --- 2. 提示信息 ---
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📋 提示:显示所有读者的借阅历史记录，包括借书时间、归还时间和当前状态");
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // --- 3. 中间表格 ---
        table = new JTable();
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshTable();
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- 4. 底部统计信息 ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(240, 240, 240));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============ 事件监听 ============

        // 搜索按钮
        btnSearch.addActionListener(e -> performSearch());

        // 搜索框回车
        txtSearchUser.addActionListener(e -> performSearch());

        // ★ 统一的重置按钮
        btnReset.addActionListener(e -> {
            txtSearchUser.setText("");
            cmbStatusFilter.setSelectedIndex(0);
            table.clearSelection(); // 添加这行
            performSearch();
        });

        // 状态筛选
        cmbStatusFilter.addActionListener(e -> performSearch());

        // 刷新按钮
        btnRefresh.addActionListener(e -> {
            refreshTable();
            JOptionPane.showMessageDialog(this, "数据已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        // 导出按钮
        btnExport.addActionListener(e -> exportToCSV());

        // 初始化统计
        updateStats();
    }

    /**
     * 刷新表格数据
     */
    private void refreshTable() {
        try {
            model = bookDAO.getAllBorrowRecordsModelForAdmin();
            table.setModel(model);

            // 设置列宽
            if (table.getColumnCount() > 0) {
                table.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
                table.getColumnModel().getColumn(1).setPreferredWidth(60);  // 图书ID
                table.getColumnModel().getColumn(2).setPreferredWidth(180); // 图书名称
                table.getColumnModel().getColumn(3).setPreferredWidth(60);  // 用户ID
                table.getColumnModel().getColumn(4).setPreferredWidth(100); // 用户名
                table.getColumnModel().getColumn(5).setPreferredWidth(150); // 借出日期
                table.getColumnModel().getColumn(6).setPreferredWidth(150); // ★ 归还日期
                table.getColumnModel().getColumn(7).setPreferredWidth(80);  // 是否归还
                table.getColumnModel().getColumn(8).setPreferredWidth(200); // 状态/处理结果
            }

            // 设置排序器
            sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);

            // 清空搜索框
            if (txtSearchUser != null) {
                txtSearchUser.setText("");
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
     * ★ 执行搜索和筛选（精准匹配用户名）
     */
    private void performSearch() {
        if (sorter == null) {
            return;
        }

        String searchText = txtSearchUser.getText().trim();
        String selectedStatus = (String) cmbStatusFilter.getSelectedItem();

        // 组合过滤条件
        RowFilter<DefaultTableModel, Object> combinedFilter = null;

        // 1. 用户名过滤（第5列，索引4）- 精准匹配
        RowFilter<DefaultTableModel, Object> userFilter = null;
        if (!searchText.isEmpty()) {
            // ★ 使用 "^...$" 进行精准匹配（不区分大小写）
            userFilter = RowFilter.regexFilter("(?i)^" + Pattern.quote(searchText) + "$", 4);
        }

        // 2. ★ 状态过滤（第8列，索引7）- 映射显示文字到实际数据
        RowFilter<DefaultTableModel, Object> statusFilter = null;
        if (!"全部记录".equals(selectedStatus)) {
            String actualStatus;
            switch (selectedStatus) {
                case "未归还":
                    actualStatus = "未归还";  // 数据库中存储的是"未归还"
                    break;
                case "已归还":
                    actualStatus = "已归还";
                    break;
                case "已遗失":
                    actualStatus = "遗失";  // 数据库中存储的是"遗失"
                    break;
                default:
                    actualStatus = selectedStatus;
            }
            statusFilter = RowFilter.regexFilter(actualStatus, 7);
        }

        // 3. 组合过滤器
        if (userFilter != null && statusFilter != null) {
            combinedFilter = RowFilter.andFilter(java.util.Arrays.asList(userFilter, statusFilter));
        } else if (userFilter != null) {
            combinedFilter = userFilter;
        } else if (statusFilter != null) {
            combinedFilter = statusFilter;
        }

        sorter.setRowFilter(combinedFilter);
        updateStats();

        // 提示搜索结果
        if (!searchText.isEmpty() && table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "未找到用户名为 [" + searchText + "] 的借阅记录。\n\n" +
                            "提示：请输入完整的用户名（精准匹配）",
                    "搜索结果",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * ★ 更新底部统计信息
     */
    private void updateStats() {
        if (statsLabel == null || table == null) {
            return;
        }

        int totalCount = table.getRowCount(); // 筛选后的行数
        int borrowedCount = 0;  // ★ 改名：已借出
        int returnedCount = 0;  // 已归还
        int lostCount = 0;      // 已遗失

        for (int i = 0; i < totalCount; i++) {
            String returnStatus = (String) table.getValueAt(i, 7);

            if ("未归还".equals(returnStatus)) {
                borrowedCount++;  // ★ 统计"未归还"（显示为"已借出"）
            } else if ("已归还".equals(returnStatus)) {
                returnedCount++;
            } else if ("遗失".equals(returnStatus)) {
                lostCount++;
            }
        }

        String statsText = String.format(
                "当前显示: %d 条  |  未归还: %d 本  |  已归还: %d 本  |  已遗失: %d 本",
                totalCount, borrowedCount, returnedCount, lostCount
        );
        statsLabel.setText(statsText);

        // 根据状态设置颜色
        if (borrowedCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34)); // 橙色
        } else if (lostCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43)); // 红色
        } else {
            statsLabel.setForeground(new Color(39, 174, 96)); // 绿色
        }
    }

    /**
     * 导出数据到CSV文件
     */
    private void exportToCSV() {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "没有数据可以导出！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存CSV文件");
        fileChooser.setSelectedFile(new File("读者借阅记录_" + System.currentTimeMillis() + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            try (FileWriter writer = new FileWriter(fileToSave)) {
                // 写入BOM（UTF-8标记，让Excel正确识别中文）
                writer.write('\ufeff');

                // 写入表头
                for (int i = 0; i < table.getColumnCount(); i++) {
                    writer.append(table.getColumnName(i));
                    if (i < table.getColumnCount() - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");

                // 写入数据
                for (int i = 0; i < table.getRowCount(); i++) {
                    for (int j = 0; j < table.getColumnCount(); j++) {
                        Object value = table.getValueAt(i, j);
                        String cellValue = value != null ? value.toString() : "";

                        // 处理包含逗号的内容
                        if (cellValue.contains(",")) {
                            cellValue = "\"" + cellValue + "\"";
                        }

                        writer.append(cellValue);
                        if (j < table.getColumnCount() - 1) {
                            writer.append(",");
                        }
                    }
                    writer.append("\n");
                }

                JOptionPane.showMessageDialog(this,
                        "数据已成功导出到：\n" + fileToSave.getAbsolutePath() +
                                "\n\n共导出 " + table.getRowCount() + " 条记录",
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
