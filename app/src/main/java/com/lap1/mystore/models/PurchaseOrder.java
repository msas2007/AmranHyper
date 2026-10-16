package com.lap1.mystore.models;

public class PurchaseOrder {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private int id;
    private String orderNumber;
    private int supplierId;
    private String supplierName;
    private double total;
    private String status;
    private String notes;
    private int createdByUserId;
    private String createdAt;
    private int itemsCount;

    public PurchaseOrder() {}

    public PurchaseOrder(int id, String orderNumber, int supplierId, String supplierName,
                         double total, String status, String notes, int createdByUserId, String createdAt) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.total = total;
        this.status = status;
        this.notes = notes;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(int createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getItemsCount() { return itemsCount; }
    public void setItemsCount(int itemsCount) { this.itemsCount = itemsCount; }

    public String getStatusArabic() {
        if (status == null) return "مسودة";
        switch (status) {
            case STATUS_DRAFT: return "مسودة";
            case STATUS_REQUESTED: return "تم الطلب";
            case STATUS_RECEIVED: return "مستلم";
            case STATUS_CANCELLED: return "ملغي";
            default: return status;
        }
    }
}
