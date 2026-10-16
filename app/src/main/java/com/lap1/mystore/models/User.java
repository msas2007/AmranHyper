package com.lap1.mystore.models;

public class User {
    private int id;
    private String username;
    private String fullName;
    private String role;
    private boolean isActive;
    private String createdAt;
    private String lastLoginAt;

    public User(int id, String username, String fullName, String role, boolean isActive, String createdAt, String lastLoginAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return (fullName != null && !fullName.trim().isEmpty()) ? fullName : username; }
    public String getRole() { return (role != null && !role.trim().isEmpty()) ? role : "admin"; }
    public boolean isActive() { return isActive; }
    public String getCreatedAt() { return createdAt; }
    public String getLastLoginAt() { return lastLoginAt; }
}
