package com.library.ui;

import com.library.dao.BookDAO;
import com.library.entity.User;
import com.library.exception.BusinessException;
import com.library.exception.DBException;
import com.library.util.SessionManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

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
    private JPanel adminCrudPanel;
    private JButton btnAddBook;
    private JButton btnUpdateBook;
    private JButton btnDeleteBook;

    // 遗失处理按钮
    private JButton btnLost;

    public BookPanel(User user) {
        this.currentUser = user;
        this.isAdmin = "admin".equals(SessionManager.getCurrentUser().getRole());

        setLayout(new BorderLayout());

        // ============================================================
        // 1. 顶部查询和操作面板
        // ============================================================
        JPanel topPanel = new JPanel(new BorderLayout());

        // --- 1.1 左侧：查询部分 ---
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtSearch = new JTextField(20);
        btnSearch = new JButton("🔍 搜索图书");
        btnResetSearch = new JButton("↺ 重置");

        searchPanel.add(new JLabel("书名关键词:"));
        searchPanel.add(txtSearch);
        searchPanel.add(btnSearch);
        searchPanel.add(btnResetSearch);

        topPanel.add(searchPanel, BorderLayout.WEST);

        // --- 1.2 右侧：用户操作部分 (借阅/归还/遗失) ---
        JPanel userActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnBorrow = new JButton("📥 借阅选中图书");
        JButton btnReturn = new JButton("📤 归还选中图书");
        userActionPanel.add(btnBorrow);
        userActionPanel.add(btnReturn);

        // 如果是管理员，添加遗失处理按钮
        if (isAdmin) {
            btnLost = new JButton("⚠️ 遗失处理");
            userActionPanel.add(btnLost);
        }

        topPanel.add(userActionPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // ============================================================
        // 2. 管理员 CRUD 面板 (仅管理员可见)
        // ============================================================
        if (isAdmin) {
            adminCrudPanel = new JPanel();

            // ★ 为按钮添加图标
            btnAddBook = new JButton("➕ 新增图书");
            btnUpdateBook = new JButton("✏️ 修改信息");
            btnDeleteBook = new JButton("🗑️ 删除图书");

            // 设置按钮样式
            btnAddBook.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            btnUpdateBook.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            btnDeleteBook.setFont(new Font("微软雅黑", Font.PLAIN, 12));

            adminCrudPanel.add(btnAddBook);
            adminCrudPanel.add(btnUpdateBook);
            adminCrudPanel.add(btnDeleteBook);

            // 将 CRUD 面板组合到顶部区域下方
            JPanel northContainer = new JPanel(new BorderLayout());
            northContainer.add(topPanel, BorderLayout.NORTH);
            northContainer.add(adminCrudPanel, BorderLayout.CENTER);
            add(northContainer, BorderLayout.NORTH);
        }

        // ============================================================
        // 3. 中间表格
        // ============================================================
        bookTable = new JTable();
        bookTable.getTableHeader().setReorderingAllowed(false);

        refreshTable(null);
        add(new JScrollPane(bookTable), BorderLayout.CENTER);

        // ============================================================
        // 4. 事件监听器绑定
        // ============================================================

        // 搜索与重置
        btnSearch.addActionListener(e -> refreshTable(txtSearch.getText()));
        btnResetSearch.addActionListener(e -> {
            txtSearch.setText("");
            refreshTable(null);
        });

        // 普通操作
        btnBorrow.addActionListener(e -> borrowBookAction());
        btnReturn.addActionListener(e -> returnBookAction());

        // 管理员操作
        if (isAdmin) {
            btnAddBook.addActionListener(e -> addBookAction());
            btnUpdateBook.addActionListener(e -> updateBookAction());
            btnDeleteBook.addActionListener(e -> deleteBookAction());
            btnLost.addActionListener(e -> handleBookLostAction());
        }
    }

    /**
     * 刷新表格数据
     * @param keyword 搜索关键词，null 或空字符串表示查询所有
     */
    private void refreshTable(String keyword) {
        boolean onlyAvailable = !isAdmin;
        DefaultTableModel model = bookDAO.getBookModel(keyword, onlyAvailable);
        bookTable.setModel(model);

        // 处理搜索结果为空的情况
        if (model.getRowCount() == 0 && keyword != null && !keyword.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "未找到符合关键词 [" + keyword + "] 的图书。",
                    "搜索结果", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ============================================================
    // 业务逻辑方法
    // ============================================================

    // 1. 借阅图书
    private void borrowBookAction() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要借阅的图书。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bookId = (int) bookTable.getValueAt(row, 0);
        String title = (String) bookTable.getValueAt(row, 1);
        String status = (String) bookTable.getValueAt(row, 3);

        if (!"可借阅".equals(status)) {
            JOptionPane.showMessageDialog(this, "该书已被借出或不可用，无法借阅。", "操作失败", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "确认借阅图书 [" + title + "] 吗？", "借阅确认", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                bookDAO.borrowBook(bookId, currentUser.getId());
                refreshTable(null);
                JOptionPane.showMessageDialog(this, "图书 [" + title + "] 借阅成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException | BusinessException ex) {
                JOptionPane.showMessageDialog(this, "借阅失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 2. 归还图书
    private void returnBookAction() {
        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要归还的图书。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bookId = (int) bookTable.getValueAt(row, 0);
        String title = (String) bookTable.getValueAt(row, 1);
        String status = (String) bookTable.getValueAt(row, 3);

        if (!"已借出".equals(status)) {
            JOptionPane.showMessageDialog(this, "该书当前状态为 [" + status + "]，无需归还。", "操作失败", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "确认归还图书 [" + title + "] 吗？", "归还确认", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                bookDAO.returnBook(bookId, currentUser.getId());
                refreshTable(null);
                JOptionPane.showMessageDialog(this, "图书 [" + title + "] 归还成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException | BusinessException ex) {
                JOptionPane.showMessageDialog(this, "归还失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 3. 新增图书
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
                JOptionPane.showMessageDialog(this, "图书 [" + title + "] 新增成功!", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this, "新增失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 4. 修改图书
    private void updateBookAction() {
        if (!isAdmin) return;

        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请选择要修改的图书。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bookId = (int) bookTable.getValueAt(row, 0);
        String oldTitle = (String) bookTable.getValueAt(row, 1);
        String oldAuthor = (String) bookTable.getValueAt(row, 2);

        Frame parent = JOptionPane.getFrameForComponent(this);
        BookInputDialog dialog = new BookInputDialog(parent, "修改图书信息 ID: " + bookId, oldTitle, oldAuthor);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String newTitle = dialog.getNewTitle();
            String newAuthor = dialog.getNewAuthor();
            try {
                bookDAO.updateBook(bookId, newTitle, newAuthor);
                refreshTable(null);
                JOptionPane.showMessageDialog(this, "图书信息修改成功!", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this, "修改失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 5. 删除图书
    private void deleteBookAction() {
        if (!isAdmin) return;

        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请选择要删除的图书。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bookId = (int) bookTable.getValueAt(row, 0);
        String title = (String) bookTable.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this, "确认删除图书 [" + title + "] 吗？\n此操作不可撤销！", "删除确认", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                bookDAO.deleteBook(bookId);
                refreshTable(null);
                JOptionPane.showMessageDialog(this, "图书删除成功!");
            } catch (DBException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 6. 图书遗失处理
    private void handleBookLostAction() {
        if (!isAdmin) return;

        int row = bookTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要处理的图书。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bookId = (int) bookTable.getValueAt(row, 0);
        String status = (String) bookTable.getValueAt(row, 3);

        if (!"已借出".equals(status) && !"borrowed".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "只有处于 [已借出] 状态的图书才能进行遗失处理。", "操作失败", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Frame parent = JOptionPane.getFrameForComponent(this);
        LossResolutionDialog dialog = new LossResolutionDialog(parent, bookId);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String resolutionType = dialog.getResolutionType();
            double amount = dialog.getAmount();

            try {
                bookDAO.handleBookLost(bookId, resolutionType, amount);
                refreshTable(null);

                String msg = "图书遗失处理成功！\n方式: " + ("Replacement".equals(resolutionType) ? "新书替换" : "罚款 " + amount + "元");
                JOptionPane.showMessageDialog(this, msg, "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (DBException | BusinessException ex) {
                JOptionPane.showMessageDialog(this, "处理失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}