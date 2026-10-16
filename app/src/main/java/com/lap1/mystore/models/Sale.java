package com.lap1.mystore.models;

public class Sale {
    private int id;
    private String invoiceNumber;
    private int customerId;
    private double total;
    private double paid;
    private double remaining;
    private String paymentStatus;
    private String saleDate;

    public Sale(int id, String invoiceNumber, int customerId, double total, double paid, double remaining, String paymentStatus, String saleDate) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.customerId = customerId;
        this.total = total;
        this.paid = paid;
        this.remaining = remaining;
        this.paymentStatus = paymentStatus;
        this.saleDate = saleDate;
    }

    public int getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public int getCustomerId() { return customerId; }
    public double getTotal() { return total; }
    public double getPaid() { return paid; }
    public double getRemaining() { return remaining; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getSaleDate() { return saleDate; }
}
