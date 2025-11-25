package com.library.ui;

import com.library.dao.LogDAO;
import com.library.exception.DBException;
import javax.swing.*;
import java.awt.*;

public class LogViewerPanel extends JPanel {
    private LogDAO logDAO = new LogDAO();
    private JTable logTable;

    public LogViewerPanel() {
        setLayout(new BorderLayout());

        // --- 顶部操作面板 ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // 标题
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📝 系统日志管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // 操作按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh = new JButton("🔄 刷新日志");
        JButton btnClear = new JButton("🗑️ 清空所有日志");
        btnClear.setForeground(new Color(231, 76, 60)); // 红色文字

        buttonPanel.add(btnRefresh);
        buttonPanel.add(btnClear);

        topPanel.add(titlePanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- 提示信息 ---
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("📋 系统操作日志记录，可用于审计和问题追踪");
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // --- 中间表格 ---
        logTable = new JTable();
        logTable.getTableHeader().setReorderingAllowed(false);
        refreshTable();
        add(new JScrollPane(logTable), BorderLayout.CENTER);

        // ============ 事件监听 ============

        btnRefresh.addActionListener(e -> {
            refreshTable();
            JOptionPane.showMessageDialog(this, "日志已刷新", "提示", JOptionPane.INFORMATION_MESSAGE);
        });

        btnClear.addActionListener(e -> clearLogsAction());
    }

    private void refreshTable() {
        logTable.setModel(logDAO.getAllLogsModel());

        // 设置列宽
        if (logTable.getColumnCount() > 0) {
            logTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
            logTable.getColumnModel().getColumn(1).setPreferredWidth(150); // 时间
            logTable.getColumnModel().getColumn(2).setPreferredWidth(100); // 操作人
            logTable.getColumnModel().getColumn(3).setPreferredWidth(400); // 操作内容
        }
    }

    private void clearLogsAction() {
        String message = "⚠️ 警告：确定要永久清空所有系统日志吗？\n\n" +
                "此操作将删除所有历史记录，不可撤销！\n" +
                "建议在清空前先导出备份。";

        int confirm = JOptionPane.showConfirmDialog(this,
                message,
                "清空确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                logDAO.clearAllLogs();
                JOptionPane.showMessageDialog(this,
                        "所有日志已成功清空。",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);
                refreshTable();
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this,
                        "清空失败: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}