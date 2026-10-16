package com.lap1.mystore.models;

public class StoreSettings {
    private int id;
    private String storeName;
    private String address;
    private String phone;
    private String currency;
    private String invoiceFooter;
    private String createdAt;
    private String updatedAt;

    public StoreSettings(int id, String storeName, String address, String phone, String currency, String invoiceFooter, String createdAt, String updatedAt) {
        this.id = id;
        this.storeName = (storeName != null && !storeName.trim().isEmpty()) ? storeName : "عمران هايبر";
        this.address = address;
        this.phone = phone;
        this.currency = (currency != null && !currency.trim().isEmpty()) ? currency : "ر.ي";
        this.invoiceFooter = invoiceFooter;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() { return id; }
    public String getStoreName() { return storeName; }
    public String getAddress() { return address != null ? address : ""; }
    public String getPhone() { return phone != null ? phone : ""; }
    public String getCurrency() { return currency; }
    public String getInvoiceFooter() { return invoiceFooter != null ? invoiceFooter : ""; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
