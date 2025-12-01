package com.library.ui;

import com.library.dao.BookDAO;
import com.library.entity.User;
import com.library.exception.DBException;
import com.library.util.SessionManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 图书管理面板 - 仅包含图书的增删改查功能
 * 管理员：可以新增、修改、删除图书
 * 普通用户：只能查看可借阅的图书
 */
public class BookPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private JTable bookTable;
    private User currentUser;
    private boolean isAdmin;

    // UI 组件引用
    private JTextField txtSearch;
    private JButton btnSearch;
    private JButton btnResetSearch;

    // 管理员操作组件
    private JButton btnAddBook;
    private JButton btnUpdateBook;
    private JButton btnDeleteBook;

    public BookPanel(User user) {
        this.currentUser = user;
        this.isAdmin = "admin".equals(SessionManager.getCurrentUser().getRole());

        setLayout(new BorderLayout());

        // ============================================================
        // 1. 顶部面板
        // ============================================================
        JPanel topPanel = new JPanel(new BorderLayout());

        // --- 标题面板 ---
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("📚 图书管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        // --- 搜索面板 ---
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.add(new JLabel("书名关键词:"));

        txtSearch = new JTextField(20);
        searchPanel.add(txtSearch);

        btnSearch = new JButton("🔍 搜索图书");
        btnResetSearch = new JButton("↺ 重置");
        searchPanel.add(btnSearch);
        searchPanel.add(btnResetSearch);

        // --- 管理员操作按钮面板（仅管理员可见）---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        if (isAdmin) {
            btnAddBook = new JButton("➕ 新增图书");
            btnUpdateBook = new JButton("✏️ 修改信息");
            btnDeleteBook = new JButton("🗑️ 删除图书");

            buttonPanel.add(btnAddBook);
            buttonPanel.add(btnUpdateBook);
            buttonPanel.add(btnDeleteBook);
        }

        // --- 组合控制面板 ---
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(titlePanel, BorderLayout.NORTH);
        controlPanel.add(searchPanel, BorderLayout.CENTER);
        if (isAdmin) {
            controlPanel.add(buttonPanel, BorderLayout.SOUTH);
        }

        topPanel.add(controlPanel, BorderLayout.CENTER);

        // ★ 提示信息面板（放在搜索框下面）
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        String infoText = isAdmin ?
                "💡 提示：您可以新增、修改、删除图书信息（遗失/已删除的图书无法修改或删除）" :
                "💡 提示：您可以查看图书列表";
        JLabel infoLabel = new JLabel(infoText);
        infoLabel.setForeground(new Color(52, 152, 219));
        infoPanel.add(infoLabel);

        // ★ 将顶部面板和提示信息组合
        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(infoPanel, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // ============================================================
        // 2. 中间表格
        // ============================================================
        bookTable = new JTable();
        bookTable.getTableHeader().setReorderingAllowed(false);
        refreshTable(null);
        add(new JScrollPane(bookTable), BorderLayout.CENTER);

        // ============================================================
        // 3. 事件监听器绑定
        // ============================================================

        // 搜索与重置
        btnSearch.addActionListener(e -> performSearch());
        txtSearch.addActionListener(e -> performSearch()); // 回车搜索

        btnResetSearch.addActionListener(e -> {
            txtSearch.setText("");
            refreshTable(null);
            bookTable.clearSelection(); // 添加这行，取消选中
        });

        // 管理员操作
        if (isAdmin) {
            btnAddBook.addActionListener(e -> addBookAction());
            btnUpdateBook.addActionListener(e -> updateBookAction());
            btnDeleteBook.addActionListener(e -> deleteBookAction());
        }
    }

    /**
     * 执行搜索
     */
    private void performSearch() {
        String keyword = txtSearch.getText().trim();
        refreshTable(keyword.isEmpty() ? null : keyword);
    }

    /**
     * 刷新表格数据
     * @param keyword 搜索关键词，null 或空字符串表示查询所有
     */
    private void refreshTable(String keyword) {
        // 普通用户只能看到"可借阅"的图书，管理员可以看到所有图书
        boolean onlyAvailable = !isAdmin;
        DefaultTableModel model = bookDAO.getBookModel(keyword, onlyAvailable);
        bookTable.setModel(model);

        // 处理搜索结果为空的情况
        if (model.getRowCount() == 0 && keyword != null && !keyword.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "未找到符合关键词 [" + keyword + "] 的图书。",
                    "搜索结果",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ============================================================
    // 管理员操作方法
    // ============================================================

    /**
     * 新增图书
     */
    private void addBookAction() {
        if (!isAdmin) return;

        Frame parent = JOptionPane.getFrameForComponent(this);
        BookInputDialog dialog = new BookInputDialog(parent, "新增图书", null, null);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String title = dialog.getNewTitle();
            String author = dialog.getNewAuthor();

            try {
                bookDAO.addBook(title, author);
                refreshTable(null);
                JOptionPane.showMessageDialog(this,
                        "图书 [" + title + "] 新增成功!",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this,
                        "新增失败: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * ★ 修改图书信息（添加状态检查）
     */
    private void updateBookAction() {
        if (!isAdmin) return;

        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "请先选择要修改的图书。",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 检查列数
        if (bookTable.getColumnCount() < 4) {
            JOptionPane.showMessageDialog(this,
                    "错误：表格缺少状态列！\n当前列数: " + bookTable.getColumnCount() + "\n需要至少4列（ID、书名、作者、状态）",
                    "系统错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int bookId = (int) bookTable.getValueAt(row, 0);
        String oldTitle = (String) bookTable.getValueAt(row, 1);
        String oldAuthor = (String) bookTable.getValueAt(row, 2);

        // ★ 获取状态并去除空格
        Object statusObj = bookTable.getValueAt(row, 3);
        String status = statusObj != null ? statusObj.toString().trim() : "";

        // ★ 检查图书状态是否为"遗失"
        if ("遗失".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已遗失，无法修改信息。\n\n图书ID: %d\n书名: %s\n作者: %s\n状态: %s",
                            bookId, oldTitle, oldAuthor, status),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 检查图书状态是否为"已删除"
        if ("已删除".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已删除，无法修改信息。\n\n图书ID: %d\n书名: %s\n作者: %s\n状态: %s",
                            bookId, oldTitle, oldAuthor, status),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Frame parent = JOptionPane.getFrameForComponent(this);
        BookInputDialog dialog = new BookInputDialog(parent,
                "修改图书信息 (ID: " + bookId + ")",
                oldTitle,
                oldAuthor);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String newTitle = dialog.getNewTitle();
            String newAuthor = dialog.getNewAuthor();

            try {
                bookDAO.updateBook(bookId, newTitle, newAuthor);
                refreshTable(null);
                JOptionPane.showMessageDialog(this,
                        "图书信息修改成功!",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this,
                        "修改失败: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * ★ 删除图书（添加状态检查）
     */
    private void deleteBookAction() {
        if (!isAdmin) return;

        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "请先选择要删除的图书。",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 检查列数
        if (bookTable.getColumnCount() < 4) {
            JOptionPane.showMessageDialog(this,
                    "错误：表格缺少状态列！\n当前列数: " + bookTable.getColumnCount(),
                    "系统错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int bookId = (int) bookTable.getValueAt(row, 0);
        String title = (String) bookTable.getValueAt(row, 1);

        // ★ 获取状态并去除空格
        Object statusObj = bookTable.getValueAt(row, 3);
        String status = statusObj != null ? statusObj.toString().trim() : "";

        // ★ 检查图书状态是否为"遗失"
        if ("遗失".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已遗失，无法删除。\n\n图书ID: %d\n书名: %s\n状态: %s\n\n提示：已遗失的图书已被系统标记，无需手动删除。",
                            bookId, title, status),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 检查图书状态是否为"已删除"
        if ("已删除".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    String.format("该图书已删除，无法重复删除。\n\n图书ID: %d\n书名: %s\n状态: %s",
                            bookId, title, status),
                    "操作失败",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ★ 检查图书状态是否为"已借出"
        if ("已借出".equals(status)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    String.format("该图书当前已借出，确认删除吗？\n\n图书ID: %d\n书名: %s\n状态: %s\n\n⚠️ 删除后借阅记录仍会保留，但图书将无法再次借阅。",
                            bookId, title, status),
                    "删除确认",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        } else {
            // 正常删除确认
            int confirm = JOptionPane.showConfirmDialog(this,
                    "确认删除图书 [" + title + "] 吗？\n\n⚠️ 此操作不可撤销！",
                    "删除确认",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            bookDAO.deleteBook(bookId);
            refreshTable(null);
            JOptionPane.showMessageDialog(this,
                    "图书 [" + title + "] 删除成功!",
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (DBException ex) {
            JOptionPane.showMessageDialog(this,
                    "删除失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
