package com.library.dao;

import com.library.exception.DBException;
import com.library.util.DBHelper;
import com.library.util.SessionManager;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.Vector;

public class LogDAO {

    /**
     * 记录操作日志（不抛出异常，静默失败）
     */
    public void logOperation(String operation) {
        String sql = "INSERT INTO sys_logs (username, operation, op_time) VALUES (?, ?, NOW())";
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBHelper.getConnection();
            ps = conn.prepareStatement(sql);

            String username = SessionManager.getCurrentUser() != null
                    ? SessionManager.getCurrentUser().getUsername()
                    : "SYSTEM_UNKNOWN";

            ps.setString(1, username);
            ps.setString(2, operation);
            ps.executeUpdate();

        } catch (Exception e) {
            // 日志记录失败不影响主业务，只打印错误信息
            System.err.println("记录日志失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取所有日志记录
     */
    public DefaultTableModel getLogModel() {
        Vector<String> cols = new Vector<>();
        cols.add("ID");
        cols.add("用户名");
        cols.add("操作内容");
        cols.add("操作时间");

        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT id, username, operation, op_time FROM sys_logs ORDER BY op_time DESC";

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("username"));
                row.add(rs.getString("operation"));
                row.add(rs.getTimestamp("op_time"));
                data.add(row);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * 删除指定ID的日志
     */
    public void deleteLog(int logId) throws DBException {
        String sql = "DELETE FROM sys_logs WHERE id = ?";

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBHelper.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setInt(1, logId);

            int rows = ps.executeUpdate();

            if (rows == 0) {
                throw new DBException("日志记录不存在或已被删除。");
            }

        } catch (SQLException e) {
            throw new DBException("删除日志失败: " + e.getMessage(), e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 批量删除日志
     */
    public void deleteLogs(int[] logIds) throws DBException {
        if (logIds == null || logIds.length == 0) {
            throw new DBException("请选择要删除的日志记录。");
        }

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBHelper.getConnection();
            conn.setAutoCommit(false);

            String sql = "DELETE FROM sys_logs WHERE id = ?";
            ps = conn.prepareStatement(sql);

            for (int logId : logIds) {
                ps.setInt(1, logId);
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            throw new DBException("批量删除日志失败: " + e.getMessage(), e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 清空所有日志（危险操作）
     */
    public void clearAllLogs() throws DBException {
        String sql = "DELETE FROM sys_logs";

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBHelper.getConnection();
            ps = conn.prepareStatement(sql);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DBException("清空日志失败: " + e.getMessage(), e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 删除指定日期之前的日志
     */
    public void deleteLogsBefore(Date date) throws DBException {
        String sql = "DELETE FROM sys_logs WHERE op_time < ?";

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBHelper.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setDate(1, date);

            int rows = ps.executeUpdate();
            System.out.println("已删除 " + rows + " 条历史日志。");

        } catch (SQLException e) {
            throw new DBException("删除历史日志失败: " + e.getMessage(), e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取日志总数
     */
    public int getLogCount() {
        String sql = "SELECT COUNT(*) FROM sys_logs";

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return 0;
    }
}
