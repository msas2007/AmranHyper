package com.lap1.mystore.models;

public class Warehouse {
    private int id;
    private String name;
    private String location;
    private String notes;
    private int isActive;
    private String createdAt;

    public Warehouse() {}

    public Warehouse(int id, String name, String location, String notes, int isActive, String createdAt) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.notes = notes;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getIsActive() { return isActive; }
    public void setIsActive(int isActive) { this.isActive = isActive; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() { return name; }
}
