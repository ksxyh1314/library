package com.library.dao;

import com.library.exception.*;
import com.library.util.DBHelper;
import com.library.config.SystemConfig; // ← 导入配置类
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.Vector;

public class BookDAO {
    // ============================================================
    // ★ 新增：借阅记录信息类（用于罚款支付对话框）
    // ============================================================
    public static class BorrowRecordInfo {
        public int borrowId;
        public Timestamp borrowTime;
        public double fineAmount;
        public boolean finePaid;
        public String bookTitle;
        public String bookAuthor;
    }
    private LogDAO logDAO = new LogDAO();

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

            // ✅ 2. 检查并更新图书状态（★ 修复这里）
            String sqlUpdate = "UPDATE books SET status='borrowed' WHERE id=? AND status='available'";
            try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {
                psUpdate.setInt(1, bookId);
                int rows = psUpdate.executeUpdate();
                if (rows == 0) {
                    throw new BusinessException("该书已被借出或不可借阅！");
                }
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
            logDAO.logOperation("成功借阅图书 ID: " + bookId + ", 用户 ID: " + userId);

        } catch (BusinessException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw e;
        } catch (SQLException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw new DBException("借阅交易失败: " + e.getMessage(), e);
        } finally {
            try {
                if(conn!=null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {}
        }
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
     * 获取图书列表的 TableModel
     * @param keyword 搜索关键词（书名模糊匹配），null 表示查询所有
     * @param onlyAvailable 是否只查询可借阅的图书（true=普通用户，false=管理员）
     * @return DefaultTableModel
     */
    public DefaultTableModel getBookModel(String keyword, boolean onlyAvailable) {
        Vector<String> cols = new Vector<>();
        cols.add("图书编号");
        cols.add("书名");
        cols.add("作者");
        cols.add("状态");

        Vector<Vector<Object>> data = new Vector<>();

        StringBuilder sql = new StringBuilder("SELECT id, title, author, status FROM books WHERE 1=1");

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND title LIKE ?");
        }

        if (onlyAvailable) {
            sql.append(" AND status = 'available'");
        }

        sql.append(" ORDER BY id DESC");

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (keyword != null && !keyword.trim().isEmpty()) {
                ps.setString(paramIndex++, "%" + keyword.trim() + "%");
            }

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("title"));
                row.add(rs.getString("author"));

                // ★★★ 状态列改成中文
                String status = rs.getString("status");
                String statusText;
                switch (status) {
                    case "available":
                        statusText = "可借阅";
                        break;
                    case "borrowed":
                        statusText = "已借出";
                        break;
                    case "lost":
                        statusText = "遗失";
                        break;
                    case "deleted":
                        statusText = "已删除";
                        break;
                    default:
                        statusText = status; // 兼容其他状态
                }
                row.add(statusText);

                data.add(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // ★ 可以选择记录日志或显示错误信息
            System.err.println("查询图书列表失败: " + e.getMessage());
        } catch (DBException e) {
            throw new RuntimeException(e);
        }

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
     * ★ 获取用户的借阅记录（修复罚款显示逻辑）
     */
    /**
     * ★ 获取用户的借阅记录（简化状态显示）
     */
    public DefaultTableModel getMyBorrowRecordsModel(int userId) throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("记录ID");
        cols.add("书名");
        cols.add("借出日期");
        cols.add("应归还日期/归还日期");
        cols.add("是否归还");
        cols.add("状态");
        cols.add("罚款金额");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.id, b.title, br.borrow_time, br.return_time, br.is_returned, " +
                "br.resolution, br.fine_amount, br.fine_paid " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "WHERE br.user_id = ? " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("title"));

                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime != null ? borrowTime.toString() : "-");

                Timestamp returnTime = rs.getTimestamp("return_time");
                int isReturned = rs.getInt("is_returned");

                // ★ 第4列：应归还日期/归还日期
                if (isReturned == 1) {
                    // 已归还，显示实际归还日期
                    row.add(returnTime != null ? returnTime.toString() : "-");
                } else {
                    // 未归还，显示应归还日期
                    if (borrowTime != null) {
                        long dueTimeMillis = borrowTime.getTime() + SystemConfig.DUE_PERIOD_MILLIS;
                        Timestamp dueDate = new Timestamp(dueTimeMillis);
                        row.add(dueDate.toString());
                    } else {
                        row.add("-");
                    }
                }

                // ★ 第5列：是否归还
                String returnStatus;
                if (isReturned == 1) {
                    returnStatus = "已归还";
                } else if (isReturned == 2) {
                    returnStatus = "遗失";
                } else {
                    returnStatus = "未归还";
                }
                row.add(returnStatus);

                // ★★★ 第6列：状态（简化显示）
                String resolution = rs.getString("resolution");
                double fineAmount = rs.getDouble("fine_amount");
                String statusText;

                if (isReturned == 1) {
                    // 已归还
                    if (fineAmount > 0) {
                        // ★ 有罚款，只显示"超期罚款"
                        statusText = "超期罚款";
                    } else if (resolution != null && resolution.contains("遗失")) {
                        statusText = "遗失罚款";
                    } else {
                        statusText = "正常归还";
                    }
                } else if (isReturned == 2) {
                    // 遗失
                    if (resolution != null && resolution.contains("新书替换")) {
                        statusText = "遗失 - 新书替换";
                    } else {
                        statusText = "遗失 - 罚款处理";
                    }
                } else {
                    // 未归还，计算是否超期
                    if (borrowTime != null) {
                        long currentTime = System.currentTimeMillis();
                        long dueTimeMillis = borrowTime.getTime() + SystemConfig.DUE_PERIOD_MILLIS;

                        if (currentTime > dueTimeMillis) {
                            long overdueMillis = currentTime - dueTimeMillis;
                            long overduePeriod = SystemConfig.calculateOverduePeriod(overdueMillis);
                            statusText = String.format("已超期 %d %s", overduePeriod, SystemConfig.getTimeUnitText());
                        } else {
                            long remainingMillis = dueTimeMillis - currentTime;
                            long remainingPeriod = SystemConfig.calculateRemainingPeriod(remainingMillis);
                            statusText = String.format("借阅中（剩余 %d %s）", remainingPeriod, SystemConfig.getTimeUnitText());
                        }
                    } else {
                        statusText = "数据异常";
                    }
                }
                row.add(statusText);

                // ★ 第7列：罚款金额
                boolean finePaid = rs.getBoolean("fine_paid");

                String fineText;
                if (fineAmount > 0) {
                    if (finePaid) {
                        fineText = String.format("%.2f 元（已支付）", fineAmount);
                    } else {
                        fineText = String.format("%.2f 元（待支付）", fineAmount);
                    }
                } else {
                    fineText = "-";
                }
                row.add(fineText);

                data.add(row);
            }
        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败: " + e.getMessage(), e);
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
     * ★ 获取所有借阅记录（管理员用）- 修复遗失状态显示
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
        cols.add("状态");
        cols.add("罚款状态");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.id, br.book_id, b.title, br.user_id, u.username, " +
                "br.borrow_time, br.return_time, br.is_returned, br.resolution, " +
                "br.fine_amount, br.fine_paid " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "JOIN users u ON br.user_id = u.id " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getInt("book_id"));
                row.add(rs.getString("title"));
                row.add(rs.getInt("user_id"));
                row.add(rs.getString("username"));

                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime != null ? borrowTime.toString() : "-");

                Timestamp returnTime = rs.getTimestamp("return_time");
                int isReturned = rs.getInt("is_returned");

                // ★ 应还日期
                if (borrowTime != null) {
                    long dueTimeMillis = borrowTime.getTime() + SystemConfig.DUE_PERIOD_MILLIS;
                    Timestamp dueDate = new Timestamp(dueTimeMillis);
                    row.add(dueDate.toString());
                } else {
                    row.add("-");
                }

                // ★★★ 是否归还（修复：新书替换显示为"已归还"）
                String resolution = rs.getString("resolution");
                String returnStatus;
                if (isReturned == 1) {
                    returnStatus = "已归还";
                } else if (isReturned == 2) {
                    // ★ 检查是否是新书替换
                    if (resolution != null && resolution.contains("新书替换")) {
                        returnStatus = "已归还";  // ← 新书替换显示为"已归还"
                    } else {
                        returnStatus = "遗失";    // ← 遗失罚款显示为"遗失"
                    }
                } else {
                    returnStatus = "未归还";
                }
                row.add(returnStatus);


                // ★★★ 状态列（根据 resolution 字段判断）
                double fineAmount = rs.getDouble("fine_amount");
                String statusText;

                if (isReturned == 1) {
                    // 已归还
                    if (fineAmount > 0) {
                        statusText = "超期罚款";
                    } else if (resolution != null && resolution.contains("遗失")) {
                        statusText = "遗失罚款";
                    } else {
                        statusText = "正常归还";
                    }
                } else if (isReturned == 2) {
                    // ★★★ 遗失 - 根据 resolution 判断处理方式
                    if (resolution != null) {
                        if (resolution.contains("新书替换")) {
                            statusText = "新书替换";
                        } else if (resolution.contains("遗失") && resolution.contains("罚款")) {
                            statusText = "遗失罚款";
                        } else {
                            // 兼容旧数据
                            statusText = "遗失罚款";
                        }
                    } else {
                        // resolution 为空，根据罚款金额判断
                        if (fineAmount > 0) {
                            statusText = "遗失罚款";
                        } else {
                            statusText = "新书替换";
                        }
                    }
                } else {
                    // 未归还，计算是否超期
                    if (borrowTime != null) {
                        long currentTime = System.currentTimeMillis();
                        long dueTimeMillis = borrowTime.getTime() + SystemConfig.DUE_PERIOD_MILLIS;

                        if (currentTime > dueTimeMillis) {
                            long overdueMillis = currentTime - dueTimeMillis;
                            long overduePeriod = SystemConfig.calculateOverduePeriod(overdueMillis);
                            statusText = String.format("已超期 %d %s", overduePeriod, SystemConfig.getTimeUnitText());
                        } else {
                            long remainingMillis = dueTimeMillis - currentTime;
                            long remainingPeriod = SystemConfig.calculateRemainingPeriod(remainingMillis);
                            statusText = String.format("借阅中（剩余 %d %s）", remainingPeriod, SystemConfig.getTimeUnitText());
                        }
                    } else {
                        statusText = "数据异常";
                    }
                }
                row.add(statusText);

                // ★ 罚款状态
                boolean finePaid = rs.getBoolean("fine_paid");
                String fineText;
                if (fineAmount > 0) {
                    if (finePaid) {
                        fineText = String.format("%.2f 元（已支付）", fineAmount);
                    } else {
                        fineText = String.format("%.2f 元（待支付）", fineAmount);
                    }
                } else {
                    fineText = "-";
                }
                row.add(fineText);

                data.add(row);
            }
        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败: " + e.getMessage(), e);
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
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

            if (!"borrowed".equalsIgnoreCase(currentStatus) && !"borrowed".equalsIgnoreCase(currentStatus)) {
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
                String markLostSql = "UPDATE books SET status = 'lost' WHERE id = ?";
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
     * ★ 获取所有借阅记录（管理员用）- 显示归还时间
     * 与 getAllBorrowRecordsModel() 的区别：
     * 1. 第7列显示"归还时间"而不是"应还时间"
     * 2. 第8列不显示超期计算，只显示简单状态
     */
    /**
     * ★ 获取所有借阅记录（管理员用）- 显示归还时间
     * ★★★ 修复：新书替换的记录显示为"已归还"
     */
    public DefaultTableModel getAllBorrowRecordsModelForAdmin() throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("记录ID");
        cols.add("图书ID");
        cols.add("图书名称");
        cols.add("用户ID");
        cols.add("用户名");
        cols.add("借出日期");
        cols.add("归还日期");
        cols.add("是否归还");
        cols.add("状态");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.id, br.book_id, b.title, br.user_id, u.username, " +
                "br.borrow_time, br.return_time, br.is_returned, br.resolution, " +
                "br.fine_amount, br.fine_paid " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "JOIN users u ON br.user_id = u.id " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getInt("book_id"));
                row.add(rs.getString("title"));
                row.add(rs.getInt("user_id"));
                row.add(rs.getString("username"));

                // 借出时间
                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime != null ? borrowTime.toString() : "-");

                // 归还时间
                Timestamp returnTime = rs.getTimestamp("return_time");
                row.add(returnTime != null ? returnTime.toString() : "-");

                // ★★★ 是否归还状态（根据 resolution 判断新书替换）
                int isReturned = rs.getInt("is_returned");
                String resolution = rs.getString("resolution");
                String returnStatus;

                if (isReturned == 1) {
                    returnStatus = "已归还";
                } else if (isReturned == 2) {
                    // ★★★ 关键修复：新书替换视为"已归还"
                    if (resolution != null && resolution.contains("新书替换")) {
                        returnStatus = "已归还";  // 新书替换 → 已归还
                    } else {
                        returnStatus = "遗失";     // 遗失罚款 → 遗失
                    }
                } else {
                    returnStatus = "未归还";
                }
                row.add(returnStatus);

                // ★ 状态列（详细说明）
                double fineAmount = rs.getDouble("fine_amount");
                boolean finePaid = rs.getBoolean("fine_paid");
                String statusText;

                if (isReturned == 1) {
                    // 正常归还
                    if (fineAmount > 0) {
                        if (finePaid) {
                            statusText = String.format("超期归还（已支付罚款 %.2f 元）", fineAmount);
                        } else {
                            statusText = String.format("超期归还（待支付罚款 %.2f 元）", fineAmount);
                        }
                    } else {
                        statusText = "正常归还";
                    }
                } else if (isReturned == 2) {
                    // ★★★ 遗失状态（区分新书替换和罚款处理）
                    if (resolution != null && resolution.contains("新书替换")) {
                        statusText = "新书替换";  // 显示为"新书替换"
                    } else if (resolution != null && resolution.contains("罚款")) {
                        statusText = String.format("遗失罚款（%.2f 元）", fineAmount);
                    } else {
                        // 兼容旧数据
                        if (fineAmount > 0) {
                            statusText = String.format("遗失罚款（%.2f 元）", fineAmount);
                        } else {
                            statusText = "新书替换";
                        }
                    }
                } else {
                    // 未归还
                    if (fineAmount > 0) {
                        if (finePaid) {
                            statusText = String.format("借阅中（已支付罚款 %.2f 元）", fineAmount);
                        } else {
                            statusText = String.format("借阅中（待支付罚款 %.2f 元）", fineAmount);
                        }
                    } else {
                        statusText = "借阅中";
                    }
                }
                row.add(statusText);

                data.add(row);
            }
        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败: " + e.getMessage(), e);
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }


    /**
     * 查询用户对某本书的待支付罚款
     * @param bookId 图书ID
     * @param userId 用户ID
     * @return 罚款金额，如果没有罚款返回0
     */
    public double getPendingFine(int bookId, int userId) throws DBException {
        String sql = "SELECT fine_amount FROM borrow_records " +
                "WHERE book_id = ? AND user_id = ? AND is_returned = 0 AND fine_paid = 0";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, bookId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getDouble("fine_amount");
            }
            return 0;

        } catch (SQLException e) {
            throw new DBException("查询罚款失败: " + e.getMessage(), e);
        }
    }
    /**
     * ★ 新增：归还图书（支持罚款支付）- 重载方法
     * @param bookId 图书ID
     * @param userId 用户ID
     * @param finePayment 支付的罚款金额（如果没有罚款传0）
     */
    public void returnBook(int bookId, int userId, double finePayment) throws DBException, BusinessException {
        Connection conn = null;
        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false);

            // 1. 查询借阅记录和罚款信息
            String checkSql = "SELECT id, fine_amount, fine_paid FROM borrow_records " +
                    "WHERE book_id = ? AND user_id = ? AND is_returned = 0";

            int borrowId = 0;
            double fineAmount = 0;
            boolean finePaid = false;

            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, bookId);
                ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    borrowId = rs.getInt("id");
                    fineAmount = rs.getDouble("fine_amount");
                    finePaid = rs.getBoolean("fine_paid");
                } else {
                    throw new BusinessException("还书失败：您不是该书的借阅人，或该书已归还。");
                }
            }

            // 2. 强制检查罚款
            if (fineAmount > 0 && !finePaid) {
                if (finePayment < fineAmount) {
                    throw new BusinessException(
                            String.format("还书失败：您有待支付罚款 %.2f 元，必须先支付罚款才能归还图书。\n\n" +
                                            "请联系管理员在【超期和遗失管理】中记录罚款后，再次尝试归还。",
                                    fineAmount)
                    );
                }

                if (Math.abs(finePayment - fineAmount) > 0.01) {
                    throw new BusinessException(
                            String.format("还书失败：支付金额 %.2f 元与应付罚款 %.2f 元不符。",
                                    finePayment, fineAmount)
                    );
                }
            }

            // 3. 更新借阅记录
            String updateRecordSql;
            if (fineAmount > 0) {
                updateRecordSql = "UPDATE borrow_records " +
                        "SET return_time = NOW(), is_returned = 1, fine_paid = 1, " +
                        "resolution = CONCAT(IFNULL(resolution, ''), ' 正常归还（已支付罚款 ', ?, ' 元）') " +
                        "WHERE id = ?";
            } else {
                updateRecordSql = "UPDATE borrow_records " +
                        "SET return_time = NOW(), is_returned = 1, " +
                        "resolution = '正常归还' " +
                        "WHERE id = ?";
            }

            try (PreparedStatement ps = conn.prepareStatement(updateRecordSql)) {
                if (fineAmount > 0) {
                    ps.setDouble(1, fineAmount);
                    ps.setInt(2, borrowId);
                } else {
                    ps.setInt(1, borrowId);
                }
                ps.executeUpdate();
            }

            // 4. 更新图书状态
            String updateBookSql = "UPDATE books SET status='available' WHERE id=? AND status='borrowed'";
            try (PreparedStatement ps = conn.prepareStatement(updateBookSql)) {
                ps.setInt(1, bookId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new BusinessException("还书失败：图书状态异常，请联系管理员。");
                }
            }

            conn.commit();

            // 5. 记录日志
            if (fineAmount > 0) {
                logDAO.logOperation("成功归还图书 ID: " + bookId +
                        ", 用户 ID: " + userId +
                        ", 已支付罚款: " + fineAmount + " 元");
            } else {
                logDAO.logOperation("成功归还图书 ID: " + bookId + ", 用户 ID: " + userId);
            }

        } catch (BusinessException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw e;
        } catch (SQLException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw new DBException("归还交易失败: " + e.getMessage(), e);
        } finally {
            try {
                if(conn!=null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {}
        }
    }

    /**
     * ★ 新增：获取借阅记录的详细信息（包括罚款信息）
     * 用于支付对话框显示
     */
    public BorrowRecordInfo getBorrowRecordInfo(int bookId, int userId) throws DBException {
        String sql = "SELECT br.id, br.borrow_time, br.fine_amount, br.fine_paid, " +
                "b.title, b.author " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "WHERE br.book_id = ? AND br.user_id = ? AND br.is_returned = 0";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, bookId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                BorrowRecordInfo info = new BorrowRecordInfo();
                info.borrowId = rs.getInt("id");
                info.borrowTime = rs.getTimestamp("borrow_time");
                info.fineAmount = rs.getDouble("fine_amount");
                info.finePaid = rs.getBoolean("fine_paid");
                info.bookTitle = rs.getString("title");
                info.bookAuthor = rs.getString("author");
                return info;
            }
            return null;

        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * ★ 新增：管理员记录超期罚款（只记录罚款，不自动归还）
     * @param borrowId 借阅记录ID
     * @param fineAmount 罚款金额
     */
    public void recordOverdueFine(int borrowId, double fineAmount) throws DBException {
        String sql = "UPDATE borrow_records " +
                "SET fine_amount = ?, fine_paid = 0, " +
                "resolution = CONCAT(IFNULL(resolution, ''), ' 超期罚款: ', ?, ' 元（待支付）') " +
                "WHERE id = ? AND is_returned = 0";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, fineAmount);
            ps.setDouble(2, fineAmount);
            ps.setInt(3, borrowId);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new DBException("记录罚款失败：借阅记录不存在或已归还。");
            }

            logDAO.logOperation("管理员记录超期罚款：借阅记录ID " + borrowId +
                    ", 罚款金额: " + fineAmount + " 元（待用户归还时支付）");

        } catch (SQLException e) {
            throw new DBException("记录罚款失败: " + e.getMessage(), e);
        }
    }

    public DefaultTableModel getCurrentBorrowedBooksModel(int userId) throws DBException {
        Vector<String> cols = new Vector<>();
        cols.add("图书ID");
        cols.add("书名");
        cols.add("作者");
        cols.add("借出日期");
        cols.add("应还日期");
        cols.add("状态");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT br.book_id, b.title, b.author, br.borrow_time, " +
                "br.fine_amount, br.fine_paid " +
                "FROM borrow_records br " +
                "JOIN books b ON br.book_id = b.id " +
                "WHERE br.user_id = ? AND br.is_returned = 0 " +
                "ORDER BY br.borrow_time DESC";

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("book_id"));
                row.add(rs.getString("title"));
                row.add(rs.getString("author"));

                Timestamp borrowTime = rs.getTimestamp("borrow_time");
                row.add(borrowTime != null ? borrowTime.toString() : "-");

                if (borrowTime != null) {
                    long dueTimeMillis = borrowTime.getTime() + SystemConfig.DUE_PERIOD_MILLIS;
                    Timestamp dueDate = new Timestamp(dueTimeMillis);
                    row.add(dueDate.toString());

                    long currentTime = System.currentTimeMillis();
                    double fineAmount = rs.getDouble("fine_amount");
                    boolean finePaid = rs.getBoolean("fine_paid");

                    String status;
                    if (currentTime > dueTimeMillis) {
                        // 已超期
                        long overdueMillis = currentTime - dueTimeMillis;
                        long overduePeriod = SystemConfig.calculateOverduePeriod(overdueMillis);

                        if (fineAmount > 0 && !finePaid) {
                            // ★ 管理员已记录罚款，待用户支付
                            status = String.format("⚠ 已超期 %d %s（待支付罚款 %.2f 元）",
                                    overduePeriod,
                                    SystemConfig.getTimeUnitText(),
                                    fineAmount);
                        } else if (fineAmount > 0 && finePaid) {
                            // 罚款已支付（不应该出现这种情况）
                            status = String.format("已超期 %d %s（罚款已支付 %.2f 元，可归还）",
                                    overduePeriod,
                                    SystemConfig.getTimeUnitText(),
                                    fineAmount);
                        } else {
                            // ★ 超期但管理员未记录罚款 - 禁止归还
                            status = String.format("⚠ 已超期 %d %s（请联系管理员处理罚款）",
                                    overduePeriod,
                                    SystemConfig.getTimeUnitText());
                        }
                    } else {
                        // 未超期
                        long remainingMillis = dueTimeMillis - currentTime;
                        long remainingPeriod = SystemConfig.calculateRemainingPeriod(remainingMillis);
                        status = String.format("借阅中（剩余 %d %s）",
                                remainingPeriod,
                                SystemConfig.getTimeUnitText());
                    }
                    row.add(status);
                } else {
                    row.add("-");
                    row.add("数据异常");
                }

                data.add(row);
            }
        } catch (SQLException e) {
            throw new DBException("查询借阅记录失败: " + e.getMessage(), e);
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * ★ 新增：处理图书遗失
     * @param borrowId 借阅记录ID
     * @param bookId 图书ID
     * @param fineAmount 罚款金额（如果是新书替换则为0）
     * @param isReplacement 是否为新书替换（true=新书替换，false=罚款处理）
     */
    /**
     * ★ 处理图书遗失（确保正确写入 resolution）
     */
    public void handleBookLoss(int borrowId, int bookId, double fineAmount, boolean isReplacement)
            throws DBException, BusinessException {

        Connection conn = null;
        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false);

            // 1. 检查借阅记录是否存在且未归还
            String checkSql = "SELECT is_returned FROM borrow_records WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, borrowId);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    throw new BusinessException("借阅记录不存在");
                }

                int isReturned = rs.getInt("is_returned");
                if (isReturned == 1) {
                    throw new BusinessException("该图书已归还，无法标记为遗失");
                }
                if (isReturned == 2) {
                    throw new BusinessException("该图书已标记为遗失");
                }
            }

            // 2. 更新借阅记录
            String updateBorrowSql;
            String resolution;

            if (isReplacement) {
                // ★ 新书替换
                resolution = "遗失 - 新书替换";
                updateBorrowSql = "UPDATE borrow_records SET is_returned = 2, return_time = NOW(), " +
                        "resolution = ?, fine_amount = 0, fine_paid = 0 WHERE id = ?";
            } else {
                // ★ 罚款处理
                resolution = String.format("遗失 - 罚款处理: %.2f 元", fineAmount);
                updateBorrowSql = "UPDATE borrow_records SET is_returned = 2, return_time = NOW(), " +
                        "resolution = ?, fine_amount = ?, fine_paid = 1 WHERE id = ?";
            }

            try (PreparedStatement ps = conn.prepareStatement(updateBorrowSql)) {
                ps.setString(1, resolution);
                if (isReplacement) {
                    ps.setInt(2, borrowId);
                } else {
                    ps.setDouble(2, fineAmount);
                    ps.setInt(3, borrowId);
                }
                ps.executeUpdate();
            }

            // 3. 更新图书状态
            String updateBookSql;
            if (isReplacement) {
                // 新书替换 - 恢复为可借阅
                updateBookSql = "UPDATE books SET status = 'available' WHERE id = ?";
            } else {
                // 罚款处理 - 标记为遗失
                updateBookSql = "UPDATE books SET status = 'lost' WHERE id = ?";
            }

            try (PreparedStatement ps = conn.prepareStatement(updateBookSql)) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
            }

            conn.commit();

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw new DBException("处理遗失失败: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 归还图书（原方法 - 增加罚款检查）
     * ★ 如果有超期但未记录罚款，禁止归还
     */
    public void returnBook(int bookId, int userId) throws DBException, BusinessException {
        Connection conn = null;
        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false);

            // ✅ 1. 查询借阅记录和罚款信息
            String checkSql = "SELECT id, borrow_time, fine_amount, fine_paid FROM borrow_records " +
                    "WHERE book_id = ? AND user_id = ? AND is_returned = 0";

            int borrowId = 0;
            Timestamp borrowTime = null;
            double fineAmount = 0;
            boolean finePaid = false;

            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setInt(1, bookId);
                psCheck.setInt(2, userId);
                ResultSet rs = psCheck.executeQuery();

                if (rs.next()) {
                    borrowId = rs.getInt("id");
                    borrowTime = rs.getTimestamp("borrow_time");
                    fineAmount = rs.getDouble("fine_amount");
                    finePaid = rs.getBoolean("fine_paid");
                } else {
                    throw new BusinessException("还书失败：您不是该书的借阅人，或该书已归还。");
                }
            }

            // ✅ 2. 检查是否超期
            long currentTime = System.currentTimeMillis();
            long dueTimeMillis = borrowTime.getTime() + SystemConfig.DUE_PERIOD_MILLIS;
            boolean isOverdue = currentTime > dueTimeMillis;

            // ★ 3. 超期检查逻辑
            if (isOverdue) {
                // 超期了，必须先记录罚款
                if (fineAmount == 0) {
                    // 管理员还未记录罚款
                    long overdueMillis = currentTime - dueTimeMillis;
                    long overduePeriod = SystemConfig.calculateOverduePeriod(overdueMillis);

                    throw new BusinessException(
                            String.format("还书失败：该图书已超期 %d %s，但管理员尚未记录罚款。\n\n" +
                                            "请联系管理员在【超期和遗失管理】中记录罚款后，再次尝试归还。",
                                    overduePeriod, SystemConfig.getTimeUnitText())
                    );
                }

                // 已记录罚款但未支付
                if (!finePaid) {
                    throw new BusinessException(
                            String.format("还书失败：您有待支付罚款 %.2f 元。\n\n" +
                                            "请点击【支付罚款并归还】按钮完成支付。",
                                    fineAmount)
                    );
                }
            }

            // ✅ 4. 更新借阅记录
            String sqlUpdateRecord = "UPDATE borrow_records " +
                    "SET return_time = NOW(), is_returned = 1, " +
                    "resolution = '正常归还' " +
                    "WHERE id = ?";

            try (PreparedStatement psUpdateRecord = conn.prepareStatement(sqlUpdateRecord)) {
                psUpdateRecord.setInt(1, borrowId);
                psUpdateRecord.executeUpdate();
            }

            // ✅ 5. 更新图书状态
            String sqlUpdateBook = "UPDATE books SET status='available' WHERE id=? AND status='borrowed'";
            try (PreparedStatement psUpdateBook = conn.prepareStatement(sqlUpdateBook)) {
                psUpdateBook.setInt(1, bookId);
                psUpdateBook.executeUpdate();
            }

            conn.commit();
            logDAO.logOperation("成功归还图书 ID: " + bookId + ", 用户 ID: " + userId);

        } catch (BusinessException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw e;
        } catch (SQLException e) {
            try { if(conn!=null) conn.rollback(); } catch (SQLException ex) {}
            throw new DBException("归还交易失败: " + e.getMessage(), e);
        } finally {
            try {
                if(conn!=null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {}
        }
    }

} // ← 类结束

