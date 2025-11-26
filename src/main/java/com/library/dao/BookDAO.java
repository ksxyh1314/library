package com.library.dao;

import com.library.exception.*;
import com.library.util.DBHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.Vector;

public class BookDAO {
    private LogDAO logDAO = new LogDAO();

    // --- 事务操作 ---

    public void borrowBook(int bookId, int userId) throws DBException, BusinessException {
        Connection conn = null;
        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false);

            // ✅ 1. 检查用户是否已借阅该书且未归还
            String checkSql = "SELECT COUNT(*) FROM borrow_records " +
                    "WHERE user_id = ? AND book_id = ? AND is_returned = 0";
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setInt(1, userId);
                psCheck.setInt(2, bookId);
                ResultSet rs = psCheck.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new BusinessException("您已借阅该图书，请勿重复借阅！");
                }
            }

            // ✅ 2. 检查并更新图书状态
            String sqlUpdate = "UPDATE books SET status='已借出' WHERE id=? AND status='可借阅'";
            try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {
                psUpdate.setInt(1, bookId);
                int rows = psUpdate.executeUpdate();
                if (rows == 0) throw new BusinessException("该书已被借出或不可借阅！");
            }

            // ✅ 3. 插入借阅记录（设置 is_returned = 0）
            String sqlInsert = "INSERT INTO borrow_records (user_id, book_id, borrow_time, is_returned) " +
                    "VALUES (?, ?, NOW(), 0)";
            try (PreparedStatement psInsert = conn.prepareStatement(sqlInsert)) {
                psInsert.setInt(1, userId);
                psInsert.setInt(2, bookId);
                psInsert.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw new DBException("借阅交易失败", e);
        } finally {
            try { if(conn!=null) conn.close(); } catch (SQLException e) {}
        }
        logDAO.logOperation("成功借阅图书 ID: " + bookId + ", 用户 ID: " + userId);
    }


    // --- BookDAO.java 文件内部 ---

    // 关键变化：方法签名现在接收当前操作人的 ID (userId)
    public void returnBook(int bookId, int userId) throws DBException, BusinessException {
        Connection conn = null;
        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false);

            // ✅ 1. 更新借阅记录（同时设置 return_time 和 is_returned）
            String sqlUpdateRecord = "UPDATE borrow_records " +
                    "SET return_time = NOW(), is_returned = 1 " +
                    "WHERE book_id = ? AND user_id = ? AND is_returned = 0";

            int recordsUpdated;
            try (PreparedStatement psUpdateRecord = conn.prepareStatement(sqlUpdateRecord)) {
                psUpdateRecord.setInt(1, bookId);
                psUpdateRecord.setInt(2, userId);
                recordsUpdated = psUpdateRecord.executeUpdate();
            }

            if (recordsUpdated == 0) {
                throw new BusinessException("还书失败：您不是该书的借阅人，或该书已归还。");
            }

            // ✅ 2. 更新图书状态为 '可借阅'
            String sqlUpdateBook = "UPDATE books SET status='可借阅' WHERE id=? AND status='已借出'";
            try (PreparedStatement psUpdateBook = conn.prepareStatement(sqlUpdateBook)) {
                psUpdateBook.setInt(1, bookId);
                psUpdateBook.executeUpdate();
            }

            conn.commit();
        } catch (BusinessException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw e;
        } catch (SQLException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw new DBException("归还交易失败", e);
        } finally {
            try { if(conn!=null) conn.close(); } catch (SQLException e) {}
        }
        logDAO.logOperation("成功归还图书 ID: " + bookId + ", 验证用户 ID: " + userId);
    }



    // --- CRUD 操作 ---

    public void addBook(String title, String author) throws DBException {
        String sql = "INSERT INTO books (title, author, status) VALUES (?, ?, '可借阅')";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.executeUpdate();
            logDAO.logOperation("新增图书: " + title);
        } catch (SQLException e) {
            throw new DBException("入库图书失败: " + e.getMessage(), e);
        }
    }

    public void updateBook(int id, String newTitle, String newAuthor) throws DBException {
        String sql = "UPDATE books SET title=?, author=? WHERE id=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newTitle);
            ps.setString(2, newAuthor);
            ps.setInt(3, id);
            ps.executeUpdate();
            logDAO.logOperation("修改图书 ID " + id + " 信息");
        } catch (SQLException e) {
            throw new DBException("修改图书失败: " + e.getMessage(), e);
        }
    }

    public void deleteBook(int bookId) throws DBException {
        String sql = "DELETE FROM books WHERE id=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            int rows = ps.executeUpdate();
            if(rows == 0) throw new DBException("删除失败，图书可能不存在");
            logDAO.logOperation("删除图书 ID: " + bookId);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1451) {
                throw new DBException("删除失败：该图书存在借阅记录。", e);
            }
            throw new DBException("删除图书异常: " + e.getMessage());
        }
    }

    // --- 查询操作 ---

    /**
     * ★ 单参数版本 - 查询图书列表
     */
    public DefaultTableModel getBookModel(String keyword) {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("书名");
        cols.add("作者");
        cols.add("状态");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT id, title, author, status FROM books WHERE status != '已删除'";
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += " AND title LIKE ?";
        }
        sql += " ORDER BY id ASC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (keyword != null && !keyword.trim().isEmpty()) {
                ps.setString(1, "%" + keyword + "%");
            }

            try(ResultSet rs = ps.executeQuery()){
                while(rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("title"));
                    row.add(rs.getString("author"));
                    row.add(rs.getString("status"));
                    data.add(row);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ★ 添加不可编辑功能
        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * ★ 双参数版本 - 查询图书列表（BookPanel使用）
     */
    public DefaultTableModel getBookModel(String keyword, boolean onlyAvailable) {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("书名");
        cols.add("作者");
        cols.add("状态");

        Vector<Vector<Object>> data = new Vector<>();

        // ★ 基础查询中直接排除 '已删除' 的记录
        String sql = "SELECT id, title, author, status FROM books WHERE status != '已删除'";

        // 如果是普通用户，进一步限制只能看 '可借阅'
        if (onlyAvailable) {
            sql += " AND status = '可借阅'";
        }

        // 关键词搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += " AND title LIKE ?";
        }

        sql += " ORDER BY id ASC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (keyword != null && !keyword.trim().isEmpty()) {
                ps.setString(1, "%" + keyword + "%");
            }

            try(ResultSet rs = ps.executeQuery()){
                while(rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));       // 索引0
                    row.add(rs.getString("title")); // 索引1
                    row.add(rs.getString("author"));// 索引2
                    row.add(rs.getString("status"));// 索引3 ★ 状态列
                    data.add(row);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ★ 添加不可编辑功能
        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public DefaultTableModel getBorrowStatusModel() {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("书名");
        cols.add("状态");
        cols.add("借阅人");
        cols.add("借出时间");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT b.id, b.title, b.status, u.username, br.borrow_time " +
                "FROM books b " +
                "LEFT JOIN borrow_records br ON b.id = br.book_id AND br.is_returned = 0 " +
                "LEFT JOIN users u ON br.user_id = u.id " +
                "WHERE b.status != '已删除' AND b.status != '遗失' " +
                "ORDER BY b.id ASC";

        try (Connection conn = DBHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("title"));

                String status = rs.getString("status");
                row.add(status);

                String username = rs.getString("username");
                row.add(username != null ? username : "-");

                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime != null ? borrowTime.toString() : "-");

                data.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ★ 添加不可编辑功能
        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * 根据图书状态（如 '可借阅', '已借出'）统计图书数量。
     * @param status 图书状态
     * @return 对应状态的图书数量
     */
    public int getCountByStatus(String status) throws com.library.exception.DBException {
        String sql = "SELECT COUNT(*) AS count FROM books WHERE status = ?";

        try (Connection conn = com.library.util.DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            throw new com.library.exception.DBException("查询图书状态计数失败: " + e.getMessage(), e);
        }
        return 0;
    }

    /**
     * 查询指定用户ID的所有借阅记录（包括已还和未还）。
     * ★ 未归还显示"应归还日期"，已归还显示"归还日期"
     * @param userId 目标用户的ID
     * @return 包含借阅信息的 DefaultTableModel
     */
    public DefaultTableModel getMyBorrowRecordsModel(int userId) throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("图书名称");
        cols.add("借出日期");
        cols.add("应还日期/归还日期");
        cols.add("是否归还");
        cols.add("状态/处理结果");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.id, b.title, br.borrow_time, br.return_time, br.is_returned, br.fine_amount, br.resolution " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "WHERE br.user_id = ? " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("title"));

                    Timestamp borrowTime = rs.getTimestamp("borrow_time");
                    row.add(borrowTime);

                    // ★ 计算应还日期
                    long dueTimeMillis = borrowTime.getTime() + (long)30 * 24 * 60 * 60 * 1000;
                    Timestamp dueDate = new Timestamp(dueTimeMillis);

                    // ★ 获取归还时间
                    Timestamp returnTime = rs.getTimestamp("return_time");
                    boolean hasReturned = returnTime != null;
                    String resolution = rs.getString("resolution");
                    double fine = rs.getDouble("fine_amount");

                    // ★ 根据是否归还显示不同的日期
                    if (hasReturned) {
                        row.add(returnTime);
                    } else {
                        row.add(dueDate);
                    }

                    // --- 判断 "是否归还" 列 ---
                    if (!hasReturned) {
                        row.add("未归还");
                    } else {
                        if (resolution != null && !resolution.trim().isEmpty()) {
                            row.add("遗失");
                        } else {
                            row.add("已归还");
                        }
                    }

                    // --- 判断 "状态/处理结果" 列 ---
                    if (!hasReturned) {
                        long diff = System.currentTimeMillis() - dueTimeMillis;
                        int overdueDays = (int) Math.max(0, diff / (24 * 60 * 60 * 1000));

                        if (overdueDays > 0) {
                            row.add("已超期 " + overdueDays + " 天");
                        } else {
                            row.add("借阅中");
                        }
                    } else {
                        if (resolution != null && !resolution.trim().isEmpty()) {
                            if ("遗失罚款".equals(resolution) || resolution.contains("罚款")) {
                                if (fine > 0) {
                                    row.add("遗失罚款 " + String.format("%.2f", fine) + " 元");
                                } else {
                                    row.add(resolution);
                                }
                            } else {
                                row.add(resolution);
                            }
                        } else if (fine > 0) {
                            row.add("超期罚款 " + String.format("%.2f", fine) + " 元");
                        } else {
                            row.add("正常归还");
                        }
                    }
                    data.add(row);
                }
            }
        } catch (SQLException e) {
            throw new DBException("查询用户借阅记录失败。", e);
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * ★ 获取用户当前未归还的图书（用于还书界面）
     */
    public DefaultTableModel getCurrentBorrowedBooksModel(int userId) throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("记录ID");
        cols.add("图书名称");
        cols.add("借出日期");
        cols.add("应还日期");
        cols.add("状态");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.id, b.title, br.borrow_time " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "WHERE br.user_id = ? AND br.return_time IS NULL " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Vector<Object> row = new Vector<>();

                row.add(rs.getInt("id"));
                row.add(rs.getString("title"));

                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime != null ? borrowTime.toString() : "-");

                if (borrowTime != null) {
                    long dueTimeMillis = borrowTime.getTime() + (long) 30 * 24 * 60 * 60 * 1000;
                    Timestamp dueDate = new Timestamp(dueTimeMillis);
                    row.add(dueDate.toString());

                    long currentTime = System.currentTimeMillis();
                    if (currentTime > dueTimeMillis) {
                        long overdueDays = (currentTime - dueTimeMillis) / (24 * 60 * 60 * 1000);
                        row.add("已超期 " + overdueDays + " 天");
                    } else {
                        long remainingDays = (dueTimeMillis - currentTime) / (24 * 60 * 60 * 1000);
                        row.add("借阅中（剩余 " + remainingDays + " 天）");
                    }
                } else {
                    row.add("-");
                    row.add("数据异常");
                }

                data.add(row);
            }

        } catch (SQLException e) {
            throw new DBException("加载未归还图书失败。", e);
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static final int DUE_DAYS = 30;

    /**
     * 获取管理员查看的所有借阅记录模型。
     */
    public DefaultTableModel getAllBorrowRecordsModel() throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("记录ID");
        cols.add("图书ID");
        cols.add("图书名称");
        cols.add("用户ID");
        cols.add("用户名");
        cols.add("借出日期");
        cols.add("应还日期");
        cols.add("是否归还");
        cols.add("状态/处理结果");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT DISTINCT br.id, br.book_id, b.title, br.user_id, u.username, " +
                "br.borrow_time, br.return_time, br.is_returned, br.fine_amount, br.resolution " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "JOIN users u ON br.user_id = u.id " +
                "WHERE b.status != '已删除' " +
                "ORDER BY br.id DESC";

        try (Connection conn = DBHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getInt("book_id"));
                row.add(rs.getString("title"));
                row.add(rs.getInt("user_id"));
                row.add(rs.getString("username"));

                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime);

                long dueTimeMillis = borrowTime.getTime() + (long)30 * 24 * 60 * 60 * 1000;
                Timestamp dueDate = new Timestamp(dueTimeMillis);
                row.add(dueDate);

                int isReturned = rs.getInt("is_returned");
                String resolution = rs.getString("resolution");
                double fine = rs.getDouble("fine_amount");

                String returnStatus;
                if (isReturned == 0) {
                    returnStatus = "未归还";
                } else if (resolution != null && resolution.contains("遗失")) {
                    returnStatus = "遗失";
                } else {
                    returnStatus = "已归还";
                }
                row.add(returnStatus);

                String statusInfo;
                if (isReturned == 0) {
                    long diff = System.currentTimeMillis() - dueTimeMillis;
                    int overdueDays = (int) Math.max(0, diff / (24 * 60 * 60 * 1000));

                    if (overdueDays > 0) {
                        statusInfo = "已超期 " + overdueDays + " 天";
                    } else {
                        statusInfo = "借阅中";
                    }
                } else {
                    if (resolution != null && !resolution.trim().isEmpty()) {
                        if (resolution.contains("罚款") && fine > 0) {
                            statusInfo = resolution + " " + String.format("%.2f", fine) + " 元";
                        } else {
                            statusInfo = resolution;
                        }
                    } else if (fine > 0) {
                        statusInfo = "超期罚款 " + String.format("%.2f", fine) + " 元";
                    } else {
                        statusInfo = "正常归还";
                    }
                }
                row.add(statusInfo);

                data.add(row);
            }
        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败。", e);
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * 记录超期罚款金额，更新借阅记录。
     */
    public void recordOverdueFine(int borrowId, double fineAmount) throws DBException {
        String sql = "UPDATE borrow_records SET fine_amount = ?, resolution = '超期罚款处理', is_returned = 1, return_time = NOW() WHERE id = ?";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, fineAmount);
            ps.setInt(2, borrowId);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new DBException("未找到借阅记录 ID: " + borrowId + " 或记录已处理。");
            }
            logDAO.logOperation("记录借阅ID " + borrowId + " 超期罚款，金额: " + fineAmount + "元。");

        } catch (SQLException e) {
            throw new DBException("记录罚款失败。", e);
        }
    }

    /**
     * 处理图书遗失（事务操作）。
     */
    public void handleBookLost(int bookId, String resolutionType, double amount) throws DBException, BusinessException {
        Connection conn = null;
        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false);

            String title = null;
            String author = null;
            String currentStatus = null;

            String querySql = "SELECT title, author, status FROM books WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(querySql)) {
                ps.setInt(1, bookId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    title = rs.getString("title");
                    author = rs.getString("author");
                    currentStatus = rs.getString("status");
                } else {
                    throw new BusinessException("找不到 ID 为 " + bookId + " 的图书。");
                }
            }

            if (!"已借出".equals(currentStatus) && !"borrowed".equalsIgnoreCase(currentStatus)) {
                throw new BusinessException("图书状态异常（当前: " + currentStatus + "），只有[已借出]的书才能处理遗失。");
            }

            if ("Replacement".equals(resolutionType)) {
                String insertNewSql = "INSERT INTO books (title, author, status) VALUES (?, ?, '可借阅')";
                try (PreparedStatement ps = conn.prepareStatement(insertNewSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.setString(2, author);
                    ps.executeUpdate();
                }

                String deleteOldSql = "UPDATE books SET status = '已删除' WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteOldSql)) {
                    ps.setInt(1, bookId);
                    ps.executeUpdate();
                }

                String closeRecordSql = "UPDATE borrow_records SET return_time = NOW(), is_returned = 1, fine_amount = 0, resolution = '新书替换(旧书已删/新书已上架)' WHERE book_id = ? AND is_returned = 0";
                try (PreparedStatement ps = conn.prepareStatement(closeRecordSql)) {
                    ps.setInt(1, bookId);
                    int rows = ps.executeUpdate();
                    if (rows == 0) throw new BusinessException("未找到活跃借阅记录。");
                }

                logDAO.logOperation("遗失处理: ID " + bookId + " 已删除，新书已上架替换。");

            } else {
                String markLostSql = "UPDATE books SET status = '遗失' WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(markLostSql)) {
                    ps.setInt(1, bookId);
                    ps.executeUpdate();
                }

                String closeRecordSql = "UPDATE borrow_records SET return_time = NOW(), is_returned = 1, fine_amount = ?, resolution = '遗失罚款' WHERE book_id = ? AND is_returned = 0";
                try (PreparedStatement ps = conn.prepareStatement(closeRecordSql)) {
                    ps.setDouble(1, amount);
                    ps.setInt(2, bookId);
                    int rows = ps.executeUpdate();
                    if (rows == 0) throw new BusinessException("未找到活跃借阅记录。");
                }

                logDAO.logOperation("遗失处理: ID " + bookId + " 标记为遗失，罚款: " + amount);
            }

            conn.commit();

        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
            throw new DBException("处理遗失操作失败: " + e.getMessage(), e);
        } catch (BusinessException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
            throw e;
        } finally {
            try { if (conn != null) { conn.setAutoCommit(true); conn.close(); } } catch (SQLException e) {}
        }
    }

    /**
     * 统计特定状态的图书数量。
     */
    public int getBookCountByStatus(String status) {
        String sql;
        if (status == null) {
            sql = "SELECT COUNT(*) FROM books WHERE status != '已删除'";
        } else {
            sql = "SELECT COUNT(*) FROM books WHERE status = ?";
        }

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (status != null) {
                ps.setString(1, status);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * ★ 获取管理员查看的所有借阅记录（显示归还时间而不是应还时间）
     */
    public DefaultTableModel getAllBorrowRecordsModelForAdmin() throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("图书ID");
        cols.add("图书名称");
        cols.add("用户ID");
        cols.add("用户名");
        cols.add("借出日期");
        cols.add("归还日期");
        cols.add("是否归还");
        cols.add("状态/处理结果");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.id, br.book_id, b.title, br.user_id, u.username, " +
                "br.borrow_time, br.return_time, br.is_returned, br.fine_amount, br.resolution " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "JOIN users u ON br.user_id = u.id " +
                "WHERE b.status != '已删除' " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = DBHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getInt("book_id"));
                row.add(rs.getString("title"));
                row.add(rs.getInt("user_id"));
                row.add(rs.getString("username"));

                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime);

                Timestamp returnTime = rs.getTimestamp("return_time");
                if (returnTime != null) {
                    row.add(returnTime);
                } else {
                    row.add("-");
                }

                int isReturned = rs.getInt("is_returned");
                String resolution = rs.getString("resolution");
                double fine = rs.getDouble("fine_amount");

                String returnStatus;
                if (isReturned == 0) {
                    returnStatus = "未归还";
                } else if (resolution != null && resolution.contains("遗失")) {
                    returnStatus = "遗失";
                } else {
                    returnStatus = "已归还";
                }
                row.add(returnStatus);

                String statusInfo;
                if (isReturned == 0) {
                    long dueTimeMillis = borrowTime.getTime() + (long)30 * 24 * 60 * 60 * 1000;
                    long diff = System.currentTimeMillis() - dueTimeMillis;
                    int overdueDays = (int) Math.max(0, diff / (24 * 60 * 60 * 1000));

                    if (overdueDays > 0) {
                        statusInfo = "已超期 " + overdueDays + " 天";
                    } else {
                        statusInfo = "借阅中";
                    }
                } else {
                    if (resolution != null && !resolution.trim().isEmpty()) {
                        if (resolution.contains("罚款") && fine > 0) {
                            statusInfo = resolution + " " + String.format("%.2f", fine) + " 元";
                        } else {
                            statusInfo = resolution;
                        }
                    } else if (fine > 0) {
                        statusInfo = "超期罚款 " + String.format("%.2f", fine) + " 元";
                    } else {
                        statusInfo = "正常归还";
                    }
                }
                row.add(statusInfo);

                data.add(row);
            }
        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败。", e);
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
