package com.lap1.mystore.models;

public class Customer {
    private int id;
    private String name;
    private String phone;
    private String address;
    private double balance;

    public Customer(int id, String name, String phone, String address, double balance) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.balance = balance;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public double getBalance() { return balance; }

    @Override
    public String toString() {
        return name + " (" + balance + ")";
    }
}
