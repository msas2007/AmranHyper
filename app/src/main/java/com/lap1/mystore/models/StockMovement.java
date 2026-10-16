package com.lap1.mystore.models;

public class StockMovement {
    public static final String PURCHASE = "PURCHASE";
    public static final String SALE = "SALE";
    public static final String SALE_RETURN = "SALE_RETURN";
    public static final String PURCHASE_RETURN = "PURCHASE_RETURN";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    public static final String TRANSFER_IN = "TRANSFER_IN";
    public static final String TRANSFER_OUT = "TRANSFER_OUT";

    private int id;
    private int productId;
    private String productName;
    private int warehouseId;
    private String warehouseName;
    private double quantity;
    private String movementType;
    private int referenceId;
    private int userId;
    private String notes;
    private String createdAt;

    public StockMovement() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getWarehouseId() { return warehouseId; }
    public void setWarehouseId(int warehouseId) { this.warehouseId = warehouseId; }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }

    public int getReferenceId() { return referenceId; }
    public void setReferenceId(int referenceId) { this.referenceId = referenceId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getMovementTypeArabic() {
        switch (movementType == null ? "" : movementType) {
            case PURCHASE:        return "شراء";
            case SALE:            return "بيع";
            case SALE_RETURN:     return "مرتجع بيع";
            case PURCHASE_RETURN: return "مرتجع شراء";
            case ADJUSTMENT:      return "تسوية";
            case TRANSFER_IN:     return "تحويل وارد";
            case TRANSFER_OUT:    return "تحويل صادر";
            default:              return movementType;
        }
    }
}
