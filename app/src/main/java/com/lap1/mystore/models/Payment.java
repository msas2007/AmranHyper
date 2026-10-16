package com.lap1.mystore.models;

public class Payment {
    private int id;
    private int customerId;
    private int saleId;
    private double amount;
    private String paymentDate;
    private String notes;

    public Payment(int id, int customerId, int saleId, double amount, String paymentDate, String notes) {
        this.id = id;
        this.customerId = customerId;
        this.saleId = saleId;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.notes = notes;
    }

    public int getId() { return id; }
    public int getCustomerId() { return customerId; }
    public int getSaleId() { return saleId; }
    public double getAmount() { return amount; }
    public String getPaymentDate() { return paymentDate; }
    public String getNotes() { return notes; }
}
