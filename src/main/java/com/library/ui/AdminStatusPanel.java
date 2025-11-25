package com.library.ui;

import com.library.dao.BookDAO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class AdminStatusPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable table;
    private JComboBox<String> statusFilter;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel statsLabel;

    public AdminStatusPanel() {
        setLayout(new BorderLayout());

        // --- 顶部面板 ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // 标题
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📊 馆内借阅状态一览");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // ★ 筛选面板（筛选状态+重置）
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel filterLabel = new JLabel("筛选状态:");
        statusFilter = new JComboBox<>(new String[]{"全部", "可借阅", "已借出"});

        // ★ 重置筛选按钮 - 放在筛选状态旁边
        JButton btnResetFilter = new JButton("↺ 重置");

        filterPanel.add(filterLabel);
        filterPanel.add(statusFilter);
        filterPanel.add(btnResetFilter);

        // 操作按钮面板（刷新、导出）
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh = new JButton("🔄 刷新数据");
        JButton btnExport = new JButton("📤 导出数据");

        actionPanel.add(btnRefresh);
        actionPanel.add(btnExport);

        topPanel.add(titlePanel, BorderLayout.NORTH);
        topPanel.add(filterPanel, BorderLayout.WEST);
        topPanel.add(actionPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- 提示信息 ---
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📖 以下为所有图书的当前借阅状态，包括已借出和可借阅的图书");
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // --- 表格 ---
        table = new JTable();
        table.getTableHeader().setReorderingAllowed(false);
        refresh();
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- 底部统计信息 ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(240, 240, 240));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        bottomPanel.add(statsLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- 事件监听 ---

        // 刷新按钮
        btnRefresh.addActionListener(e -> {
            refresh();
            JOptionPane.showMessageDialog(this, "数据已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        // ★ 筛选功能 - 实际实现
        statusFilter.addActionListener(e -> {
            applyFilter();
        });

        // ★ 重置筛选按钮
        btnResetFilter.addActionListener(e -> {
            statusFilter.setSelectedIndex(0); // 恢复为"全部"
            applyFilter();
        });

        // ★ 导出功能 - 实际实现
        btnExport.addActionListener(e -> {
            exportToCSV();
        });
    }

    /**
     * 刷新表格数据
     */
    private void refresh() {
        model = bookDAO.getBorrowStatusModel();
        table.setModel(model);

        // 设置列宽
        if(table.getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
            table.getColumnModel().getColumn(1).setPreferredWidth(200); // 书名
            table.getColumnModel().getColumn(2).setPreferredWidth(80);  // 状态
            table.getColumnModel().getColumn(3).setPreferredWidth(100); // 借阅人
            table.getColumnModel().getColumn(4).setPreferredWidth(150); // 时间
        }

        // 设置排序器
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        // 应用当前筛选
        applyFilter();

        // ★ 更新统计信息
        updateStats();
    }

    /**
     * ★ 应用筛选条件
     */
    private void applyFilter() {
        if (sorter == null || statusFilter == null) {
            return;
        }

        String selected = (String) statusFilter.getSelectedItem();

        if ("全部".equals(selected)) {
            // 显示所有数据
            sorter.setRowFilter(null);
        } else {
            // 根据状态筛选（状态在第2列，索引为2）
            RowFilter<DefaultTableModel, Object> filter = RowFilter.regexFilter(selected, 2);
            sorter.setRowFilter(filter);
        }

        // ★ 延迟更新统计信息，确保筛选已应用
        SwingUtilities.invokeLater(() -> updateStats());
    }

    /**
     * ★ 更新底部统计信息
     */
    private void updateStats() {
        if (statsLabel == null || table == null || model == null) {
            return;
        }

        int totalCount = table.getRowCount(); // 筛选后的行数
        int availableCount = 0;
        int borrowedCount = 0;

        // 统计原始数据（不受筛选影响）
        for (int i = 0; i < model.getRowCount(); i++) {
            String status = (String) model.getValueAt(i, 2);
            if ("可借阅".equals(status)) {
                availableCount++;
            } else if ("已借出".equals(status)) {
                borrowedCount++;
            }
        }

        // 显示统计信息
        String statsText = String.format(
                "当前显示: %d 本  |  可借阅: %d 本  |  已借出: %d 本",
                totalCount, availableCount, borrowedCount
        );
        statsLabel.setText(statsText);

        // ★ 颜色与超期遗失界面配套
        if (borrowedCount > availableCount * 0.5) {
            // 已借出较多 - 深红色（警示）
            statsLabel.setForeground(new Color(192, 0, 0));
        } else if (borrowedCount > 0) {
            // 既有可借也有已借 - 深橙色（中等）
            statsLabel.setForeground(new Color(204, 102, 0));
        } else {
            // 全部可借 - 深绿色（正常）
            statsLabel.setForeground(new Color(0, 102, 0));
        }
    }

    /**
     * ★ 导出数据到CSV文件
     */
    private void exportToCSV() {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "没有数据可以导出！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 文件选择对话框
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存CSV文件");
        fileChooser.setSelectedFile(new File("借阅状态_" + System.currentTimeMillis() + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            try (FileWriter writer = new FileWriter(fileToSave)) {
                // 写入表头
                for (int i = 0; i < table.getColumnCount(); i++) {
                    writer.append(table.getColumnName(i));
                    if (i < table.getColumnCount() - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");

                // 写入数据（使用视图中的行，考虑筛选和排序）
                for (int i = 0; i < table.getRowCount(); i++) {
                    for (int j = 0; j < table.getColumnCount(); j++) {
                        Object value = table.getValueAt(i, j);
                        String cellValue = value != null ? value.toString() : "";
                        // 处理包含逗号的内容，用引号包裹
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
                        "数据已成功导出到：\n" + fileToSave.getAbsolutePath(),
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