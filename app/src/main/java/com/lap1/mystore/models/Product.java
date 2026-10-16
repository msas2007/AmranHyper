package com.lap1.mystore.models;

public class Product {
    private int id;
    private String name;
    private String barcode;
    private int categoryId;
    private double purchasePrice;
    private double salePrice;
    private int quantity;
    private int minQuantity;
    private String expiryDate;

    public Product(int id, String name, String barcode, int categoryId, double purchasePrice, double salePrice, int quantity, int minQuantity, String expiryDate) {
        this.id = id;
        this.name = name;
        this.barcode = barcode;
        this.categoryId = categoryId;
        this.purchasePrice = purchasePrice;
        this.salePrice = salePrice;
        this.quantity = quantity;
        this.minQuantity = minQuantity;
        this.expiryDate = expiryDate;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getBarcode() { return barcode; }
    public int getCategoryId() { return categoryId; }
    public double getPurchasePrice() { return purchasePrice; }
    public double getSalePrice() { return salePrice; }
    public int getQuantity() { return quantity; }
    public int getMinQuantity() { return minQuantity; }
    public String getExpiryDate() { return expiryDate; }
}
