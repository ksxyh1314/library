package com.library.entity;

public class User {
    private int id;
    private String username;
    private String password; // 完整的属性
    private String role;
    private int isActive;    // 完整的属性

    public User(int id, String username, String password, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public User(int id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    /**
     * ★ 新增：获取密码
     */
    public String getPassword() {
        return password;
    }

    // Setters
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * ★ 新增：设置密码
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
