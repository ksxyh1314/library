package com.library.util;

import com.library.exception.DBException;
import java.sql.*;

public class DBHelper {
    // ★★★ 请务必修改这里的账号密码 ★★★
    // ✅ 修改：将 UTC 改为 Asia/Shanghai (北京时间)
    private static final String URL = "jdbc:mysql://localhost:3306/library_system?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf-8";
    private static final String USER = "root";
    private static final String PASS = "ksxyh1314";

    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException e) { e.printStackTrace(); }
    }

    public static Connection getConnection() throws DBException {
        try {
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (SQLException e) {
            throw new DBException("数据库连接失败，请检查服务是否开启", e);
        }
    }

    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        try {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        } catch (SQLException ignored) {}
    }
}