package com.library.ui;

import com.library.dao.LogDAO;
import com.library.exception.DBException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 日志查看面板 - 支持查看和删除日志
 */
public class LogViewerPanel extends JPanel {
    private LogDAO logDAO = new LogDAO();
    private JTable logTable;

    private JButton btnRefresh;
    private JButton btnDeleteSelected;
    private JButton btnClearAll;
    private JLabel lblLogCount;

    public LogViewerPanel() {
        setLayout(new BorderLayout());

        // ============================================================
        // 1. 顶部面板
        // ============================================================
        JPanel topPanel = new JPanel(new BorderLayout());

        // --- 标题面板 ---
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📋 系统日志");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // --- 操作按钮面板 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        btnRefresh = new JButton("🔄 刷新");
        btnDeleteSelected = new JButton("🗑️ 删除选中");
        btnClearAll = new JButton("⚠️ 清空所有日志");

        btnClearAll.setForeground(Color.RED);

        buttonPanel.add(btnRefresh);
        buttonPanel.add(btnDeleteSelected);
        buttonPanel.add(btnClearAll);

        // --- 统计信息面板 ---
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblLogCount = new JLabel("日志总数: 0 条");
        lblLogCount.setForeground(new Color(52, 152, 219));
        infoPanel.add(lblLogCount);

        // --- 组合顶部面板 ---
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(titlePanel, BorderLayout.NORTH);
        controlPanel.add(buttonPanel, BorderLayout.CENTER);
        controlPanel.add(infoPanel, BorderLayout.SOUTH);

        topPanel.add(controlPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // ============================================================
        // 2. 中间表格
        // ============================================================
        logTable = new JTable() {
            // ★★★ 禁用自动滚动到选中行
            @Override
            public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
                super.changeSelection(rowIndex, columnIndex, toggle, extend);
                // 不调用 scrollRectToVisible，防止自动滚动
            }
        };

        logTable.getTableHeader().setReorderingAllowed(false);
        logTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); // 允许多选
        logTable.setRowHeight(25);
        logTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));

        // ★★★ 禁用自动滚动到选中单元格
        logTable.setAutoscrolls(false);

        refreshTable();

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // ============================================================
        // 3. 事件监听器
        // ============================================================
        btnRefresh.addActionListener(e -> refreshTable());
        btnDeleteSelected.addActionListener(e -> deleteSelectedLogs());
        btnClearAll.addActionListener(e -> clearAllLogs());
    }

    /**
     * 刷新表格数据
     */
    private void refreshTable() {
        DefaultTableModel model = logDAO.getLogModel();
        logTable.setModel(model);

        // ★★★ 设置列宽：ID窄、用户名窄、操作内容自动填充、时间固定
        if (logTable.getColumnCount() >= 4) {
            // 第0列：日志ID - 很窄
            logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
            logTable.getColumnModel().getColumn(0).setMinWidth(40);
            logTable.getColumnModel().getColumn(0).setMaxWidth(70);

            // 第1列：用户名 - 窄
            logTable.getColumnModel().getColumn(1).setPreferredWidth(120);
            logTable.getColumnModel().getColumn(1).setMinWidth(100);
            logTable.getColumnModel().getColumn(1).setMaxWidth(150);

            // 第2列：操作内容 - 不设置最大宽度，让它自动填充
            logTable.getColumnModel().getColumn(2).setPreferredWidth(600);
            logTable.getColumnModel().getColumn(2).setMinWidth(400);
            // ★ 不设置 maxWidth，让它可以自动扩展

            // 第3列：操作时间 - 固定宽度
            logTable.getColumnModel().getColumn(3).setPreferredWidth(170);
            logTable.getColumnModel().getColumn(3).setMinWidth(150);
            logTable.getColumnModel().getColumn(3).setMaxWidth(190);
        }

        // ★★★ 关键：使用 AUTO_RESIZE_LAST_COLUMN 模式
        // 这样操作内容列会自动填充剩余空间，铺满面板
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // 设置左对齐
        javax.swing.table.DefaultTableCellRenderer leftRenderer =
                new javax.swing.table.DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

        for (int i = 0; i < logTable.getColumnCount(); i++) {
            logTable.getColumnModel().getColumn(i).setCellRenderer(leftRenderer);
        }

        // 更新统计信息
        int count = logDAO.getLogCount();
        lblLogCount.setText("日志总数: " + count + " 条");

        // 根据日志数量改变颜色
        if (count > 1000) {
            lblLogCount.setForeground(new Color(231, 76, 60)); // 红色
        } else if (count > 500) {
            lblLogCount.setForeground(new Color(230, 126, 34)); // 橙色
        } else {
            lblLogCount.setForeground(new Color(52, 152, 219)); // 蓝色
        }
    }

    /**
     * 删除选中的日志
     */
    private void deleteSelectedLogs() {
        int[] selectedRows = logTable.getSelectedRows();

        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "请先选择要删除的日志记录！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "确认删除选中的 " + selectedRows.length + " 条日志吗？\n\n⚠️ 此操作不可撤销！",
                "删除确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // 获取选中行的日志ID
            int[] logIds = new int[selectedRows.length];
            for (int i = 0; i < selectedRows.length; i++) {
                logIds[i] = (int) logTable.getValueAt(selectedRows[i], 0);
            }

            // 批量删除
            logDAO.deleteLogs(logIds);

            refreshTable();

            JOptionPane.showMessageDialog(this,
                    "成功删除 " + selectedRows.length + " 条日志记录！",
                    "删除成功",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "删除失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 清空所有日志
     */
    private void clearAllLogs() {
        int count = logDAO.getLogCount();

        if (count == 0) {
            JOptionPane.showMessageDialog(this,
                    "当前没有日志记录。",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "⚠️⚠️⚠️ 危险操作警告 ⚠️⚠️⚠️\n\n" +
                        "此操作将删除所有 " + count + " 条日志记录！\n" +
                        "删除后无法恢复，确认继续吗？",
                "清空日志确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // 二次确认
        String input = JOptionPane.showInputDialog(this,
                "请输入 \"CLEAR\" 以确认清空所有日志：",
                "二次确认",
                JOptionPane.WARNING_MESSAGE);

        if (!"CLEAR".equals(input)) {
            JOptionPane.showMessageDialog(this,
                    "输入不正确，操作已取消。",
                    "取消",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            logDAO.clearAllLogs();
            refreshTable();

            JOptionPane.showMessageDialog(this,
                    "所有日志已清空！",
                    "清空成功",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "清空失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
