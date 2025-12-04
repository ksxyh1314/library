package com.library.ui;

import com.library.config.SystemConfig;
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
 * 超期和遗失管理面板 - 管理员专用（简洁版）
 * 功能：处理超期罚款、遗失罚款、新书替换
 * ★ 优化：简化状态显示（只显示：超期罚款、遗失罚款、新书替换）、左对齐
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
    private Timer refreshTimer;

    public OverdueManagementPanel() {
        setLayout(new BorderLayout());

        // --- 1. 顶部操作面板 ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // 标题面板
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("⏰ 超期和遗失管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));

        // ★ 添加模式提示
        JLabel modeLabel = new JLabel("  |  " + SystemConfig.getModeDescription());
        if (SystemConfig.IS_TEST_MODE) {
            modeLabel.setForeground(new Color(231, 76, 60));
        } else {
            modeLabel.setForeground(new Color(39, 174, 96));
        }
        modeLabel.setFont(new Font("微软雅黑", Font.BOLD, 11));

        titlePanel.add(titleLabel);
        titlePanel.add(modeLabel);

        // ★ 搜索和筛选面板
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        cmbSearchType = new JComboBox<>(new String[]{"用户名", "书名"});
        searchPanel.add(cmbSearchType);

        txtSearch = new JTextField(15);
        searchPanel.add(txtSearch);

        JButton btnSearch = new JButton("🔍 搜索");
        searchPanel.add(btnSearch);

        searchPanel.add(new JLabel("  |  "));

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

        searchPanel.add(new JLabel("  |  "));

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
        recordTable.setRowHeight(28); // ★ 增加行高
        recordTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        refreshTable();

        // ★ 使用滚动面板
        JScrollPane scrollPane = new JScrollPane(recordTable);
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

        updateStats();

        // ============ 事件监听 ============

        // ★ 搜索功能
        btnSearch.addActionListener(e -> performSearch());
        txtSearch.addActionListener(e -> performSearch());

        // ★ 重置按钮
        btnReset.addActionListener(e -> {
            txtSearch.setText("");
            cmbSearchType.setSelectedIndex(0);
            cmbStatusFilter.setSelectedIndex(0);
            recordTable.clearSelection();
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
     * ★★★ 刷新表格数据（优化列宽 + 左对齐 + 滚动条）
     */
    private void refreshTable() {
        try {
            model = bookDAO.getAllBorrowRecordsModel();
            recordTable.setModel(model);

            // ★★★ 优化列宽设置（不设置 MaxWidth，允许拖动调整）
            if (recordTable.getColumnCount() > 0) {
                // 记录ID
                recordTable.getColumnModel().getColumn(0).setPreferredWidth(80);
                recordTable.getColumnModel().getColumn(0).setMinWidth(60);

                // 图书ID
                recordTable.getColumnModel().getColumn(1).setPreferredWidth(80);
                recordTable.getColumnModel().getColumn(1).setMinWidth(60);

                // 图书名称
                recordTable.getColumnModel().getColumn(2).setPreferredWidth(250);
                recordTable.getColumnModel().getColumn(2).setMinWidth(150);

                // 用户ID
                recordTable.getColumnModel().getColumn(3).setPreferredWidth(80);
                recordTable.getColumnModel().getColumn(3).setMinWidth(60);

                // 用户名
                recordTable.getColumnModel().getColumn(4).setPreferredWidth(120);
                recordTable.getColumnModel().getColumn(4).setMinWidth(80);

                // 借出日期
                recordTable.getColumnModel().getColumn(5).setPreferredWidth(180);
                recordTable.getColumnModel().getColumn(5).setMinWidth(160);

                // 应还日期
                recordTable.getColumnModel().getColumn(6).setPreferredWidth(180);
                recordTable.getColumnModel().getColumn(6).setMinWidth(160);

                // 是否归还
                recordTable.getColumnModel().getColumn(7).setPreferredWidth(100);
                recordTable.getColumnModel().getColumn(7).setMinWidth(80);

                // 状态
                recordTable.getColumnModel().getColumn(8).setPreferredWidth(200);
                recordTable.getColumnModel().getColumn(8).setMinWidth(150);

                // 罚款状态
                recordTable.getColumnModel().getColumn(9).setPreferredWidth(180);
                recordTable.getColumnModel().getColumn(9).setMinWidth(120);
            }

            // ★★★ 关键：关闭自动调整，使用滚动条
            recordTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

            // ★★★ 设置所有列左对齐
            javax.swing.table.DefaultTableCellRenderer leftRenderer = new javax.swing.table.DefaultTableCellRenderer();
            leftRenderer.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

            for (int i = 0; i < recordTable.getColumnCount(); i++) {
                recordTable.getColumnModel().getColumn(i).setCellRenderer(leftRenderer);
            }

            // ★ 设置排序器
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

            // ★ 启动定时器，每分钟刷新一次
            if (refreshTimer != null) {
                refreshTimer.stop();
            }

            refreshTimer = new Timer(60000, e -> {
                int selectedRow = recordTable.getSelectedRow();
                try {
                    DefaultTableModel newModel = bookDAO.getAllBorrowRecordsModel();
                    recordTable.setModel(newModel);
                    sorter = new TableRowSorter<>(newModel);
                    recordTable.setRowSorter(sorter);

                    // ★ 重新设置列宽和左对齐
                    if (recordTable.getColumnCount() > 0) {
                        recordTable.getColumnModel().getColumn(0).setPreferredWidth(80);
                        recordTable.getColumnModel().getColumn(1).setPreferredWidth(80);
                        recordTable.getColumnModel().getColumn(2).setPreferredWidth(250);
                        recordTable.getColumnModel().getColumn(3).setPreferredWidth(80);
                        recordTable.getColumnModel().getColumn(4).setPreferredWidth(120);
                        recordTable.getColumnModel().getColumn(5).setPreferredWidth(180);
                        recordTable.getColumnModel().getColumn(6).setPreferredWidth(180);
                        recordTable.getColumnModel().getColumn(7).setPreferredWidth(100);
                        recordTable.getColumnModel().getColumn(8).setPreferredWidth(200);
                        recordTable.getColumnModel().getColumn(9).setPreferredWidth(180);
                    }

                    recordTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

                    for (int i = 0; i < recordTable.getColumnCount(); i++) {
                        recordTable.getColumnModel().getColumn(i).setCellRenderer(leftRenderer);
                    }

                    // 恢复选中行
                    if (selectedRow >= 0 && selectedRow < recordTable.getRowCount()) {
                        recordTable.setRowSelectionInterval(selectedRow, selectedRow);
                    }

                    updateStats();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            refreshTimer.start();

        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "加载数据失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ★★★ 执行精准搜索和筛选（修复版 - 支持新书替换 + 优化提示信息）
     */
    private void performSearch() {
        if (sorter == null) {
            return;
        }

        String searchText = txtSearch.getText().trim();
        String searchType = (String) cmbSearchType.getSelectedItem();
        String selectedStatus = (String) cmbStatusFilter.getSelectedItem();

        RowFilter<DefaultTableModel, Object> combinedFilter = null;

        // 1. 精准搜索过滤
        RowFilter<DefaultTableModel, Object> searchFilter = null;
        if (!searchText.isEmpty()) {
            if ("用户名".equals(searchType)) {
                searchFilter = RowFilter.regexFilter("(?i)^" + Pattern.quote(searchText) + "$", 4);
            } else if ("书名".equals(searchType)) {
                searchFilter = RowFilter.regexFilter("(?i)^" + Pattern.quote(searchText) + "$", 2);
            }
        }

        // 2. ★★★ 状态过滤（修复版 - 根据状态列判断新书替换）
        RowFilter<DefaultTableModel, Object> statusFilter = null;
        if (!"全部记录".equals(selectedStatus)) {
            if ("未归还".equals(selectedStatus)) {
                // ★ 筛选第8列"是否归还"（索引7）= "未归还"
                statusFilter = RowFilter.regexFilter("^未归还$", 7);

            } else if ("已超期".equals(selectedStatus)) {
                // ★ 筛选第9列"状态"（索引8）包含"已超期"
                statusFilter = RowFilter.regexFilter("已超期", 8);

            } else if ("已归还".equals(selectedStatus)) {
                // ★★★ 筛选"已归还" = "已归还" OR 状态包含"新书替换"
                statusFilter = new RowFilter<DefaultTableModel, Object>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                        String returnStatus = (String) entry.getValue(7); // 是否归还
                        String statusInfo = (String) entry.getValue(8);   // 状态

                        // 包括：已归还 或 新书替换
                        return "已归还".equals(returnStatus) ||
                                (statusInfo != null && statusInfo.contains("新书替换"));
                    }
                };

            } else if ("已遗失".equals(selectedStatus)) {
                // ★★★ 筛选"遗失" = "遗失" 且 状态不包含"新书替换"
                statusFilter = new RowFilter<DefaultTableModel, Object>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                        String returnStatus = (String) entry.getValue(7); // 是否归还
                        String statusInfo = (String) entry.getValue(8);   // 状态

                        // 只包括：遗失 且 不是新书替换
                        return "遗失".equals(returnStatus) &&
                                (statusInfo == null || !statusInfo.contains("新书替换"));
                    }
                };
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

        // ★★★ 4. 根据不同的筛选条件显示不同的提示信息
        if (recordTable.getRowCount() == 0) {
            String message = buildNoResultMessage(searchText, searchType, selectedStatus);
            JOptionPane.showMessageDialog(this,
                    message,
                    "搜索结果",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * ★★★ 新增：根据筛选条件构建提示信息
     */
    private String buildNoResultMessage(String searchText, String searchType, String selectedStatus) {
        StringBuilder message = new StringBuilder();

        // 情况1：只有搜索关键词（用户名或书名）
        if (!searchText.isEmpty() && "全部记录".equals(selectedStatus)) {
            message.append("未找到").append(searchType).append("为 [").append(searchText).append("] 的借阅记录。\n\n");
            message.append("提示：请输入完整的").append(searchType).append("（精准匹配）");
        }
        // 情况2：只有状态筛选
        else if (searchText.isEmpty() && !"全部记录".equals(selectedStatus)) {
            message.append("当前没有状态为 [").append(selectedStatus).append("] 的借阅记录。\n\n");

            if ("未归还".equals(selectedStatus)) {
                message.append("提示：所有图书已归还或遗失");
            } else if ("已超期".equals(selectedStatus)) {
                message.append("提示：当前没有超期的借阅记录");
            } else if ("已归还".equals(selectedStatus)) {
                message.append("提示：暂无已归还的图书记录（包括新书替换）");
            } else if ("已遗失".equals(selectedStatus)) {
                message.append("提示：暂无遗失的图书记录（不包括新书替换）");
            }
        }
        // 情况3：搜索关键词 + 状态筛选
        else if (!searchText.isEmpty() && !"全部记录".equals(selectedStatus)) {
            message.append("未找到").append(searchType).append(" [").append(searchText).append("] ");
            message.append("状态为 [").append(selectedStatus).append("] 的借阅记录。\n\n");

            if ("未归还".equals(selectedStatus)) {
                message.append("提示：该").append(searchType).append("可能没有未归还的图书，或").append(searchType).append("不存在");
            } else if ("已超期".equals(selectedStatus)) {
                message.append("提示：该").append(searchType).append("可能没有超期的图书，或").append(searchType).append("不存在");
            } else if ("已归还".equals(selectedStatus)) {
                message.append("提示：该").append(searchType).append("可能没有已归还的图书，或").append(searchType).append("不存在");
            } else if ("已遗失".equals(selectedStatus)) {
                message.append("提示：该").append(searchType).append("可能没有遗失的图书，或").append(searchType).append("不存在");
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
        if (statsLabel == null || recordTable == null) {
            return;
        }

        int totalCount = recordTable.getRowCount();
        int unreturnedCount = 0;
        int overdueCount = 0;
        int returnedCount = 0;
        int lostCount = 0;

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

        String statsText = String.format(
                "当前显示: %d 条  |  未归还: %d 本  |  已超期: %d 本  |  已归还: %d 本  |  已遗失: %d 本",
                totalCount, unreturnedCount, overdueCount, returnedCount, lostCount
        );
        statsLabel.setText(statsText);

        if (overdueCount > 0) {
            statsLabel.setForeground(new Color(192, 57, 43));
        } else if (lostCount > 0) {
            statsLabel.setForeground(new Color(230, 126, 34));
        } else if (unreturnedCount > 0) {
            statsLabel.setForeground(new Color(41, 128, 185));
        } else {
            statsLabel.setForeground(new Color(39, 174, 96));
        }
    }

    /**
     * ★ 处理超期罚款
     */
    private void handleOverdueFine() {
        int row = recordTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要处理的借阅记录。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = recordTable.convertRowIndexToModel(row);

        // 获取记录信息
        int borrowId = (int) model.getValueAt(modelRow, 0);
        String bookTitle = (String) model.getValueAt(modelRow, 2);
        String username = (String) model.getValueAt(modelRow, 4);
        String returnStatus = (String) model.getValueAt(modelRow, 7);
        String statusInfo = (String) model.getValueAt(modelRow, 8);
        String fineStatus = (String) model.getValueAt(modelRow, 9);

        // ★ 检查是否已归还或遗失
        if ("已归还".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this, "该图书已归还，无法再记录罚款。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if ("遗失".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this, "该图书已标记为遗失，请使用【遗失处理】功能。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 检查是否已记录罚款
        if (fineStatus != null && fineStatus.contains("待支付")) {
            JOptionPane.showMessageDialog(this,
                    "该借阅记录已记录罚款：\n\n" + fineStatus + "\n\n用户归还时会自动支付。",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // ★ 检查是否超期
        if (!statusInfo.contains("已超期")) {
            JOptionPane.showMessageDialog(this,
                    "该借阅记录尚未超期，无需记录罚款。\n\n当前状态：" + statusInfo,
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // ★ 弹出罚款金额输入对话框
        String input = JOptionPane.showInputDialog(
                this,
                "图书：" + bookTitle + "\n" +
                        "借阅人：" + username + "\n" +
                        "当前状态：" + statusInfo + "\n\n" +
                        "请输入罚款金额（元）：",
                "记录超期罚款",
                JOptionPane.QUESTION_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) {
            return;
        }

        try {
            double fineAmount = Double.parseDouble(input.trim());

            if (fineAmount <= 0) {
                JOptionPane.showMessageDialog(this, "罚款金额必须大于 0！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            bookDAO.recordOverdueFine(borrowId, fineAmount);

            JOptionPane.showMessageDialog(
                    this,
                    String.format("罚款记录成功！\n\n" +
                                    "借阅记录ID：%d\n" +
                                    "罚款金额：%.2f 元\n\n" +
                                    "用户归还时需支付此罚款。",
                            borrowId, fineAmount),
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE
            );

            refreshTable();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字金额！", "错误", JOptionPane.ERROR_MESSAGE);
        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this, "记录罚款失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 处理遗失图书
     */
    private void handleBookLoss() {
        int row = recordTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要处理的借阅记录。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = recordTable.convertRowIndexToModel(row);

        int borrowId = (int) model.getValueAt(modelRow, 0);
        int bookId = (int) model.getValueAt(modelRow, 1);
        String bookTitle = (String) model.getValueAt(modelRow, 2);
        String username = (String) model.getValueAt(modelRow, 4);
        String returnStatus = (String) model.getValueAt(modelRow, 7);

        if ("已归还".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this, "该图书已归还，无法标记为遗失。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if ("遗失".equals(returnStatus)) {
            JOptionPane.showMessageDialog(this, "该图书已标记为遗失。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] options = {"罚款处理", "新书替换", "取消"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "图书：" + bookTitle + "\n借阅人：" + username + "\n\n请选择处理方式：",
                "遗失处理",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        try {
            if (choice == 0) {
                // 罚款处理
                String fineInput = JOptionPane.showInputDialog(this, "请输入遗失罚款金额（元）：");
                if (fineInput == null || fineInput.trim().isEmpty()) return;

                double fineAmount = Double.parseDouble(fineInput.trim());
                if (fineAmount <= 0) {
                    JOptionPane.showMessageDialog(this, "罚款金额必须大于 0！");
                    return;
                }

                bookDAO.handleBookLoss(borrowId, bookId, fineAmount, false);
                JOptionPane.showMessageDialog(this,
                        String.format("遗失处理成功！\n\n罚款金额：%.2f 元\n图书已标记为遗失。", fineAmount),
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);

            } else if (choice == 1) {
                // 新书替换
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "确认用户已提供新书替换？\n\n图书将恢复为可借阅状态。",
                        "确认",
                        JOptionPane.YES_NO_OPTION
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    bookDAO.handleBookLoss(borrowId, bookId, 0, true);
                    JOptionPane.showMessageDialog(this,
                            "新书替换处理成功！\n\n图书已恢复为可借阅状态。",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }

            refreshTable();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字金额！");
        } catch (DBException | BusinessException ex) {
            JOptionPane.showMessageDialog(this, "处理失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 导出为 CSV 文件
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
        fileChooser.setDialogTitle("导出借阅记录");
        fileChooser.setSelectedFile(new File("借阅记录_" + System.currentTimeMillis() + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = fileChooser.getSelectedFile();
        if (!file.getName().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }

        try (FileWriter writer = new FileWriter(file)) {
            // 写入 BOM
            writer.write('\ufeff');

            // 写入表头
            for (int i = 0; i < recordTable.getColumnCount(); i++) {
                writer.write(recordTable.getColumnName(i));
                if (i < recordTable.getColumnCount() - 1) {
                    writer.write(",");
                }
            }
            writer.write("\n");

            // 写入数据
            for (int i = 0; i < recordTable.getRowCount(); i++) {
                for (int j = 0; j < recordTable.getColumnCount(); j++) {
                    Object value = recordTable.getValueAt(i, j);
                    String cellValue = value != null ? value.toString() : "";
                    if (cellValue.contains(",")) {
                        cellValue = "\"" + cellValue + "\"";
                    }
                    writer.write(cellValue);
                    if (j < recordTable.getColumnCount() - 1) {
                        writer.write(",");
                    }
                }
                writer.write("\n");
            }

            JOptionPane.showMessageDialog(this,
                    "导出成功！\n\n文件路径：" + file.getAbsolutePath() +
                            "\n\n共导出 " + recordTable.getRowCount() + " 条记录",
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "导出失败：" + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
