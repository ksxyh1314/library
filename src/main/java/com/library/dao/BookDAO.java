package com.library.dao;

import com.library.exception.*;
import com.library.util.DBHelper;
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
            conn.setAutoCommit(false); // 开启事务

            // 1. 检查并更新状态
            String sqlUpdate = "UPDATE books SET status='已借出' WHERE id=? AND status='可借阅'";
            try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {
                psUpdate.setInt(1, bookId);
                int rows = psUpdate.executeUpdate();
                if (rows == 0) throw new BusinessException("该书已被借出，无法借阅！");
            }

            // 2. 插入记录
            String sqlInsert = "INSERT INTO borrow_records (user_id, book_id) VALUES (?, ?)";
            try (PreparedStatement psInsert = conn.prepareStatement(sqlInsert)) {
                psInsert.setInt(1, userId);
                psInsert.setInt(2, bookId);
                psInsert.executeUpdate();
            }

            conn.commit(); // 提交
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
            conn.setAutoCommit(false); // 开启事务

            // 1. 更新借阅记录的 return_time，并约束 user_id
            // 关键变化：SQL中加入了 AND user_id=? 的条件
            String sqlUpdateRecord = "UPDATE borrow_records SET return_time=NOW() WHERE book_id=? AND user_id=? AND return_time IS NULL";

            int recordsUpdated;
            try (PreparedStatement psUpdateRecord = conn.prepareStatement(sqlUpdateRecord)) {
                psUpdateRecord.setInt(1, bookId);
                psUpdateRecord.setInt(2, userId); // 传入当前用户ID进行校验
                recordsUpdated = psUpdateRecord.executeUpdate();
            }

            if (recordsUpdated == 0) {
                // 如果没有记录被更新，说明该用户不是借阅人，或记录不存在
                throw new BusinessException("还书失败：您不是该书的借阅人，或该书已归还。");
            }

            // 2. 更新图书状态为 '可借阅'
            String sqlUpdateBook = "UPDATE books SET status='可借阅' WHERE id=? AND status='已借出'";
            try (PreparedStatement psUpdateBook = conn.prepareStatement(sqlUpdateBook)) {
                psUpdateBook.setInt(1, bookId);
                psUpdateBook.executeUpdate();
            }

            conn.commit();
        } catch (BusinessException e) {
            // 捕获业务逻辑错误并回滚
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw e; // 将业务异常抛给 UI 层处理
        } catch (SQLException e) {
            // 捕获数据库错误并回滚
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

    public DefaultTableModel getBookModel(String keyword) {
        // ... (省略查询表格实现，确保返回 ID(0), Title(1), Author(2), Status(3)) ...
        Vector<String> cols = new Vector<>();
        cols.add("ID"); cols.add("书名"); cols.add("作者"); cols.add("状态");
        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT id, title, author, status FROM books";
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += " WHERE title LIKE ?";
        }

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
        } catch (Exception e) { e.printStackTrace(); }
        return new DefaultTableModel(data, cols);
    }

    public DefaultTableModel getBorrowStatusModel() {
        /**
         * 获取所有图书的实时状态列表（用于管理员查询）。
         * 包含：书名、当前状态、借阅人（如有）、借出时间（如有）。
         * ★ 修改点：过滤掉状态为 '已删除' 的图书。
         */
            Vector<String> cols = new Vector<>();
            cols.add("ID");
            cols.add("书名");
            cols.add("状态");
            cols.add("借阅人");
            cols.add("借出时间");

            Vector<Vector<Object>> data = new Vector<>();

            // ★ SQL 逻辑解释：
            // 1. 查询 books 表 (b)。
            // 2. 左连接 borrow_records (br)，条件是必须为"未归还" (is_returned=0)。
            //    这样可以只获取当前正在进行的借阅信息。
            // 3. 左连接 users (u) 获取用户名。
            // 4. WHERE b.status != '已删除' <--- 关键：过滤掉已删除的旧书
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

                    // 如果有借阅人，显示用户名，否则显示 "-"
                    String username = rs.getString("username");
                    row.add(username != null ? username : "-");

                    // 如果有借出时间，显示时间，否则显示 "-"
                    Timestamp borrowTime = rs.getTimestamp("borrow_time");
                    row.add(borrowTime != null ? borrowTime.toString() : "-");

                    data.add(row);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new DefaultTableModel(data, cols);
    }
    // --- BookDAO.java 文件内部 ---
// ... (在其他方法如 getBookModel, getBorrowStatusModel 之后添加) ...

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
    // --- BookDAO.java 文件内部 ---

    /**
     * 查询指定用户ID的所有借阅记录（包括已还和未还）。
     * @param userId 目标用户的ID
     * @return 包含借阅信息的 DefaultTableModel
     */
    public DefaultTableModel getMyBorrowRecordsModel(int userId) throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("图书名称");
        cols.add("借出日期");
        cols.add("应还日期");
        cols.add("是否归还"); // 未归还/已归还/遗失
        cols.add("状态/处理结果");

        Vector<Vector<Object>> data = new Vector<>();

        // ★ 关键修复：使用 return_time IS NOT NULL 来判断是否已归还
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
                    row.add(dueDate);

                    // ★ 关键修复：使用 return_time 而不是 is_returned 来判断
                    Timestamp returnTime = rs.getTimestamp("return_time");
                    boolean hasReturned = returnTime != null;
                    String resolution = rs.getString("resolution");
                    double fine = rs.getDouble("fine_amount");

                    // --- 判断 "是否归还" 列 ---
                    if (!hasReturned) {
                        // 未归还 (return_time IS NULL)
                        row.add("未归还");
                    } else {
                        // 已归还或已处理
                        if (resolution != null && !resolution.trim().isEmpty()) {
                            // 遗失处理
                            row.add("遗失");
                        } else {
                            // 正常归还
                            row.add("已归还");
                        }
                    }

                    // --- 判断 "状态/处理结果" 列 ---
                    if (!hasReturned) {
                        // 未归还 - 检查是否超期
                        long diff = System.currentTimeMillis() - dueTimeMillis;
                        int overdueDays = (int) Math.max(0, diff / (24 * 60 * 60 * 1000));

                        if (overdueDays > 0) {
                            row.add("已超期 " + overdueDays + " 天");
                        } else {
                            row.add("借阅中");
                        }
                    } else {
                        // 已归还或已处理
                        if (resolution != null && !resolution.trim().isEmpty()) {
                            // 显示处理方案
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
                            // 超期罚款
                            row.add("超期罚款 " + String.format("%.2f", fine) + " 元");
                        } else {
                            // 正常归还
                            row.add("正常归还");
                        }
                    }
                    data.add(row);
                }
            }
        } catch (SQLException e) {
            throw new DBException("查询用户借阅记录失败。", e);
        }
        return new DefaultTableModel(data, cols);
    }
    /**
     * 查询图书列表。可根据关键词和是否只显示“可借阅”进行筛选。
     * @param keyword 搜索关键词 (书名)
     * @param onlyAvailable 是否只显示状态为“可借阅”的图书
     * @return DefaultTableModel
     */
    /**
     * 查询图书列表。
     * 修改点：增加了 status != '已删除' 的基础过滤条件。
     * @param keyword 搜索关键词
     * @param onlyAvailable 是否只显示“可借阅”
     */
    public DefaultTableModel getBookModel(String keyword, boolean onlyAvailable) {
        Vector<String> cols = new Vector<>();
        cols.add("ID"); cols.add("书名"); cols.add("作者"); cols.add("状态");
        Vector<Vector<Object>> data = new Vector<>();

        // ★ 关键修改：
        // 基础查询中直接排除 '已删除' 的记录。
        // 这样，无论是管理员还是普通用户，都看不到被标记删除的旧书。
        String sql = "SELECT id, title, author, status FROM books WHERE status != '已删除'";

        // 如果是普通用户，进一步限制只能看 '可借阅'
        if (onlyAvailable) {
            sql += " AND status = '可借阅'";
        }

        // 关键词搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += " AND title LIKE ?";
        }

        sql += " ORDER BY id ASC"; // 按ID正序排列

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 绑定参数
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
        } catch (Exception e) { e.printStackTrace(); }
        return new DefaultTableModel(data, cols);
    }
    // ★★★ 在 BookDAO.java 中新增这个方法 ★★★
// 请完整复制此方法到 BookDAO.java 文件中

    /**
     * 查询指定用户ID当前借阅的图书（仅未归还的）。
     * 用于还书界面，只显示 return_time IS NULL 的记录。
     * @param userId 目标用户的ID
     * @return 包含当前借阅信息的 DefaultTableModel
     */
    public DefaultTableModel getCurrentBorrowedBooksModel(int userId) {
        Vector<String> cols = new Vector<>();
        cols.add("图书ID");
        cols.add("书名");
        cols.add("作者");
        cols.add("借出时间");

        Vector<Vector<Object>> data = new Vector<>();

        // ★★★ 关键SQL：WHERE return_time IS NULL 确保只查未归还的书 ★★★
        String sql = "SELECT br.book_id, b.title, b.author, br.borrow_time " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "WHERE br.user_id = ? AND br.return_time IS NULL " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = com.library.util.DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try(ResultSet rs = ps.executeQuery()){
                while(rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("book_id"));
                    row.add(rs.getString("title"));
                    row.add(rs.getString("author"));
                    row.add(rs.getTimestamp("borrow_time"));
                    data.add(row);
                }
            }
        } catch (Exception e) {
            System.err.println("查询当前借阅图书失败: " + e.getMessage());
            e.printStackTrace();
        }

        return new DefaultTableModel(data, cols);
    }
    // --- com.library.dao.BookDAO.java (新增方法) ---

    private static final int DUE_DAYS = 30; // 假设借阅期限是 30 天
    /**
     * 获取管理员查看的所有借阅记录模型。
     * ★ 改进点：在遗失罚款情况下显示具体罚款金额
     */
    public DefaultTableModel getAllBorrowRecordsModel() throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("图书ID");
        cols.add("图书名称");
        cols.add("用户ID");
        cols.add("用户名");
        cols.add("借出日期");
        cols.add("应还日期");
        cols.add("是否归还"); // 修正后的显示：遗失/已归还/未归还
        cols.add("状态/处理结果");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.id, br.book_id, b.title, br.user_id, u.username, " +
                "br.borrow_time, br.is_returned, br.fine_amount, br.resolution " +
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

                boolean isReturned = rs.getBoolean("is_returned");
                String resolution = rs.getString("resolution");
                double fine = rs.getDouble("fine_amount");

                // 计算应还日期 (用于显示)
                long dueTimeMillis = borrowTime.getTime() + (long)30 * 24 * 60 * 60 * 1000;
                Timestamp dueDate = new Timestamp(dueTimeMillis);
                row.add(dueDate);

                // =========================================================
                // ★ 关键修正点：判断 "是否归还" 列的显示值
                // =========================================================
                if (!isReturned) {
                    // 1. 未归还 (正在借阅中)
                    row.add("未归还");
                } else {
                    // 2. 已结束 (is_returned = true)

                    // 检查是否有处理方案（遗失/罚款记录）
                    if (resolution != null && !resolution.trim().isEmpty()) {
                        // 2A. 遗失处理 (无论罚款还是替换，都是遗失结账)
                        row.add("遗失");
                    } else {
                        // 2B. 正常归还 (is_returned=true 且 resolution为空)
                        row.add("已归还");
                    }
                }

                // =========================================================
                // ★ "状态/处理结果" 列 - 显示具体信息
                // =========================================================
                if (!isReturned) {
                    // 计算超期天数
                    long diff = System.currentTimeMillis() - dueTimeMillis;
                    int overdueDays = (int) Math.max(0, diff / (24 * 60 * 60 * 1000));

                    if (overdueDays > 0) {
                        row.add("已超期 " + overdueDays + " 天");
                    } else {
                        row.add("借阅中");
                    }
                } else {
                    // 已结束的处理
                    if (resolution != null && !resolution.trim().isEmpty()) {
                        // ★ 改进：遗失罚款情况下，显示具体罚款金额
                        if ("遗失罚款".equals(resolution) || resolution.contains("罚款")) {
                            // 显示罚款金额
                            if (fine > 0) {
                                row.add("遗失罚款 " + String.format("%.2f", fine) + " 元");
                            } else {
                                row.add(resolution); // 如果没有金额，显示原始resolution
                            }
                        } else {
                            // 新书替换等其他方案
                            row.add(resolution);
                        }
                    } else if (fine > 0) {
                        // 超期罚款
                        row.add("超期罚款 " + String.format("%.2f", fine) + " 元");
                    } else {
                        // 正常归还
                        row.add("正常归还");
                    }
                }
                data.add(row);
            }
        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败。", e);
        }
        return new DefaultTableModel(data, cols);
    }
    // --- com.library.dao.BookDAO.java (新增方法) ---

    /**
     * 记录超期罚款金额，更新借阅记录。
     * * @param borrowId 借阅记录ID
     * @param fineAmount 罚款金额
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
     * 分支逻辑：
     * 1. 若为【罚款】：旧书变"遗失"，记录罚款。
     * 2. 若为【新书替换】：旧书变"已删除"，新书自动上架(可借阅)，结清借阅记录。
     */
    public void handleBookLost(int bookId, String resolutionType, double amount) throws DBException, BusinessException {
        Connection conn = null;
        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false); // 开启事务

            // 1. 获取旧书信息 (书名、作者、当前状态)
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

            // 校验状态
            if (!"已借出".equals(currentStatus) && !"borrowed".equalsIgnoreCase(currentStatus)) {
                throw new BusinessException("图书状态异常（当前: " + currentStatus + "），只有[已借出]的书才能处理遗失。");
            }

            // 2. 根据处理类型执行不同逻辑
            if ("Replacement".equals(resolutionType)) {
                // ============================================
                // 逻辑 A: 新书替换
                // ============================================

                // A1. 新书上架 (使用旧书的标题和作者)
                String insertNewSql = "INSERT INTO books (title, author, status) VALUES (?, ?, '可借阅')";
                try (PreparedStatement ps = conn.prepareStatement(insertNewSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.setString(2, author);
                    ps.executeUpdate();

                    // (可选) 获取新书ID用于日志
                    // ResultSet genKeys = ps.getGeneratedKeys();
                }

                // A2. 旧书删除 (逻辑删除：标记为 '已删除'，以保留借阅历史)
                // 注意：如果执行 DELETE FROM books，会因为外键约束报错或丢失历史记录
                String deleteOldSql = "UPDATE books SET status = '已删除' WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteOldSql)) {
                    ps.setInt(1, bookId);
                    ps.executeUpdate();
                }

                // A3. 更新借阅记录 (备注：新书替换)
                String closeRecordSql = "UPDATE borrow_records SET return_time = NOW(), is_returned = 1, fine_amount = 0, resolution = '新书替换(旧书已删/新书已上架)' WHERE book_id = ? AND is_returned = 0";
                try (PreparedStatement ps = conn.prepareStatement(closeRecordSql)) {
                    ps.setInt(1, bookId);
                    int rows = ps.executeUpdate();
                    if (rows == 0) throw new BusinessException("未找到活跃借阅记录。");
                }

                logDAO.logOperation("遗失处理: ID " + bookId + " 已删除，新书已上架替换。");

            } else {
                // ============================================
                // 逻辑 B: 罚款处理
                // ============================================

                // B1. 旧书标记为 "遗失"
                String markLostSql = "UPDATE books SET status = '遗失' WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(markLostSql)) {
                    ps.setInt(1, bookId);
                    ps.executeUpdate();
                }

                // B2. 更新借阅记录 (记录罚款)
                String closeRecordSql = "UPDATE borrow_records SET return_time = NOW(), is_returned = 1, fine_amount = ?, resolution = '遗失罚款' WHERE book_id = ? AND is_returned = 0";
                try (PreparedStatement ps = conn.prepareStatement(closeRecordSql)) {
                    ps.setDouble(1, amount);
                    ps.setInt(2, bookId);
                    int rows = ps.executeUpdate();
                    if (rows == 0) throw new BusinessException("未找到活跃借阅记录。");
                }

                logDAO.logOperation("遗失处理: ID " + bookId + " 标记为遗失，罚款: " + amount);
            }

            // 3. 提交事务
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
    // --- com.library.dao.BookDAO.java 内部添加 ---

    /**
     * 统计特定状态的图书数量。
     * @param status 图书状态 (如 '已借出', '可借阅', '遗失')。如果不传(null)，则统计总数(排除已删除)。
     */
    public int getBookCountByStatus(String status) {
        String sql;
        if (status == null) {
            // 统计总数 (排除已删除的)
            sql = "SELECT COUNT(*) FROM books WHERE status != '已删除'";
        } else {
            // 统计特定状态
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
}