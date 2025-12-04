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
 * ★★★ 修复：筛选逻辑使用正确的列索引
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

        // 搜索和筛选面板
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        searchPanel.add(new JLabel("用户名全称:"));
        txtSearchUser = new JTextField(15);
        searchPanel.add(txtSearchUser);

        JButton btnSearch = new JButton("🔍 搜索用户");
        searchPanel.add(btnSearch);

        searchPanel.add(new JLabel("  |  "));

        searchPanel.add(new JLabel("筛选状态:"));
        cmbStatusFilter = new JComboBox<>(new String[]{"全部记录", "未归还", "已归还", "已遗失"});
        searchPanel.add(cmbStatusFilter);

        searchPanel.add(new JLabel("  |  "));

        JButton btnReset = new JButton("↺ 重置");
        searchPanel.add(btnReset);

        // 操作按钮面板
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
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        refreshTable();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // --- 4. 底部统计信息 ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(240, 240, 240));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ============ 事件监听 ============

        btnSearch.addActionListener(e -> performSearch());
        txtSearchUser.addActionListener(e -> performSearch());

        btnReset.addActionListener(e -> {
            txtSearchUser.setText("");
            cmbStatusFilter.setSelectedIndex(0);
            table.clearSelection();
            performSearch();
        });

        cmbStatusFilter.addActionListener(e -> performSearch());

        btnRefresh.addActionListener(e -> {
            refreshTable();
            JOptionPane.showMessageDialog(this, "数据已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        btnExport.addActionListener(e -> exportToCSV());

        updateStats();
    }

    /**
     * 刷新表格数据
     */
    private void refreshTable() {
        try {
            model = bookDAO.getAllBorrowRecordsModelForAdmin();
            table.setModel(model);

            // 调整列宽
            if (table.getColumnCount() > 0) {
                table.getColumnModel().getColumn(0).setPreferredWidth(60);
                table.getColumnModel().getColumn(0).setMinWidth(60);
                table.getColumnModel().getColumn(0).setMaxWidth(80);

                table.getColumnModel().getColumn(1).setPreferredWidth(60);
                table.getColumnModel().getColumn(1).setMinWidth(60);
                table.getColumnModel().getColumn(1).setMaxWidth(80);

                table.getColumnModel().getColumn(2).setPreferredWidth(200);
                table.getColumnModel().getColumn(2).setMinWidth(150);

                table.getColumnModel().getColumn(3).setPreferredWidth(60);
                table.getColumnModel().getColumn(3).setMinWidth(60);
                table.getColumnModel().getColumn(3).setMaxWidth(80);

                table.getColumnModel().getColumn(4).setPreferredWidth(100);
                table.getColumnModel().getColumn(4).setMinWidth(80);

                table.getColumnModel().getColumn(5).setPreferredWidth(160);
                table.getColumnModel().getColumn(5).setMinWidth(160);

                table.getColumnModel().getColumn(6).setPreferredWidth(160);
                table.getColumnModel().getColumn(6).setMinWidth(160);

                table.getColumnModel().getColumn(7).setPreferredWidth(80);
                table.getColumnModel().getColumn(7).setMinWidth(80);
                table.getColumnModel().getColumn(7).setMaxWidth(100);

                table.getColumnModel().getColumn(8).setPreferredWidth(200);
                table.getColumnModel().getColumn(8).setMinWidth(150);
            }

            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

            // 左对齐
            javax.swing.table.DefaultTableCellRenderer leftRenderer = new javax.swing.table.DefaultTableCellRenderer();
            leftRenderer.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

            for (int i = 0; i < table.getColumnCount(); i++) {
                table.getColumnModel().getColumn(i).setCellRenderer(leftRenderer);
            }

            sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);

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
     * ★★★ 执行搜索和筛选（修复：使用正确的列索引，优化提示信息）
     */
    private void performSearch() {
        if (sorter == null) {
            return;
        }

        String searchText = txtSearchUser.getText().trim();
        String selectedStatus = (String) cmbStatusFilter.getSelectedItem();

        RowFilter<DefaultTableModel, Object> combinedFilter = null;

        // 1. 用户名过滤（第5列，索引4）
        RowFilter<DefaultTableModel, Object> userFilter = null;
        if (!searchText.isEmpty()) {
            userFilter = RowFilter.regexFilter("(?i)^" + Pattern.quote(searchText) + "$", 4);
        }

        // 2. ★★★ 状态过滤（第8列"是否归还"，索引7）
        RowFilter<DefaultTableModel, Object> statusFilter = null;
        if (!"全部记录".equals(selectedStatus)) {
            if ("未归还".equals(selectedStatus)) {
                statusFilter = RowFilter.regexFilter("^未归还$", 7);
            } else if ("已归还".equals(selectedStatus)) {
                statusFilter = RowFilter.regexFilter("^已归还$", 7);
            } else if ("已遗失".equals(selectedStatus)) {
                statusFilter = RowFilter.regexFilter("^遗失$", 7);
            }
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

        // ★★★ 4. 根据不同的筛选条件显示不同的提示信息
        if (table.getRowCount() == 0) {
            String message = buildNoResultMessage(searchText, selectedStatus);
            JOptionPane.showMessageDialog(this,
                    message,
                    "搜索结果",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * ★★★ 新增：根据筛选条件构建提示信息
     */
    private String buildNoResultMessage(String searchText, String selectedStatus) {
        StringBuilder message = new StringBuilder();

        // 情况1：只有用户名筛选
        if (!searchText.isEmpty() && "全部记录".equals(selectedStatus)) {
            message.append("未找到用户名为 [").append(searchText).append("] 的借阅记录。\n\n");
            message.append("提示：请输入完整的用户名（精准匹配）");
        }
        // 情况2：只有状态筛选
        else if (searchText.isEmpty() && !"全部记录".equals(selectedStatus)) {
            message.append("当前没有状态为 [").append(selectedStatus).append("] 的借阅记录。\n\n");

            if ("未归还".equals(selectedStatus)) {
                message.append("提示：所有图书已归还或遗失");
            } else if ("已归还".equals(selectedStatus)) {
                message.append("提示：暂无已归还的图书记录");
            } else if ("已遗失".equals(selectedStatus)) {
                message.append("提示：暂无遗失的图书记录");
            }
        }
        // 情况3：用户名 + 状态筛选
        else if (!searchText.isEmpty() && !"全部记录".equals(selectedStatus)) {
            message.append("未找到用户 [").append(searchText).append("] 状态为 [").append(selectedStatus).append("] 的借阅记录。\n\n");

            if ("未归还".equals(selectedStatus)) {
                message.append("提示：该用户可能没有未归还的图书，或用户名不存在");
            } else if ("已归还".equals(selectedStatus)) {
                message.append("提示：该用户可能没有已归还的图书，或用户名不存在");
            } else if ("已遗失".equals(selectedStatus)) {
                message.append("提示：该用户可能没有遗失的图书，或用户名不存在");
            }
        }
        // 情况4：没有任何筛选条件（不应该出现）
        else {
            message.append("没有找到符合条件的借阅记录。");
        }

        return message.toString();
    }


    /**
     * ★ 更新底部统计信息
     */
    private void updateStats() {
        if (statsLabel == null || table == null) {
            return;
        }

        int totalCount = table.getRowCount();
        int borrowedCount = 0;
        int returnedCount = 0;
        int lostCount = 0;

        for (int i = 0; i < totalCount; i++) {
            // ★ 读取第8列（索引7）"是否归还"
            String returnStatus = (String) table.getValueAt(i, 7);

            if ("未归还".equals(returnStatus)) {
                borrowedCount++;
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

        if (lostCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43));
        } else if (borrowedCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34));
        } else {
            statsLabel.setForeground(new Color(39, 174, 96));
        }
    }

    /**
     * 导出数据到CSV文件
     */
    private void exportToCSV() {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "没有数据可以导出！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存CSV文件");
        fileChooser.setSelectedFile(new File("读者借阅记录_" + System.currentTimeMillis() + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            try (FileWriter writer = new FileWriter(fileToSave)) {
                writer.write('\ufeff');

                for (int i = 0; i < table.getColumnCount(); i++) {
                    writer.append(table.getColumnName(i));
                    if (i < table.getColumnCount() - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");

                for (int i = 0; i < table.getRowCount(); i++) {
                    for (int j = 0; j < table.getColumnCount(); j++) {
                        Object value = table.getValueAt(i, j);
                        String cellValue = value != null ? value.toString() : "";

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
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
