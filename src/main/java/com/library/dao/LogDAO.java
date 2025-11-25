package com.library.dao;

import com.library.exception.DBException;
import com.library.util.DBHelper;
import com.library.util.SessionManager;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.Vector;

public class LogDAO {

    public void logOperation(String operation) {
        String username = SessionManager.getCurrentUsername();
        String sql = "INSERT INTO sys_logs (username, operation) VALUES (?, ?)";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, operation);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("日志记录失败: " + e.getMessage());
        }
    }

    public DefaultTableModel getAllLogsModel() {
        Vector<String> cols = new Vector<>();
        cols.add("ID"); cols.add("操作时间"); cols.add("操作人"); cols.add("操作内容");
        Vector<Vector<Object>> data = new Vector<>();

        String sql = "SELECT id, op_time, username, operation FROM sys_logs ORDER BY id DESC";
        try (Connection conn = DBHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getTimestamp("op_time"));
                row.add(rs.getString("username"));
                row.add(rs.getString("operation"));
                data.add(row);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return new DefaultTableModel(data, cols);
    }

    public void clearAllLogs() throws DBException {
        String sql = "TRUNCATE TABLE sys_logs";
        try (Connection conn = DBHelper.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new DBException("清空日志失败: " + e.getMessage(), e);
        }
    }
}
