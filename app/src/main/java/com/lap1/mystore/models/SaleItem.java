package com.lap1.mystore.models;

public class SaleItem {
    private int id;
    private int saleId;
    private int productId;
    private int quantity;
    private double unitPrice;
    private double purchasePrice;
    private double total;

    public SaleItem(int id, int saleId, int productId, int quantity, double unitPrice, double purchasePrice, double total) {
        this.id = id;
        this.saleId = saleId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.purchasePrice = purchasePrice;
        this.total = total;
    }

    public int getId() { return id; }
    public int getSaleId() { return saleId; }
    public int getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getPurchasePrice() { return purchasePrice; }
    public double getTotal() { return total; }
}
