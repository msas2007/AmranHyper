package com.lap1.mystore.activities;

import android.app.DatePickerDialog;
import android.text.InputType;
import android.widget.CheckBox;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class ProductsActivity extends AppCompatActivity {

    public static class ProductItem {
        int id;
        String name;
        String barcode;
        int categoryId;
        String categoryName;
        double purchasePrice;
        double salePrice;
        double quantity;
        double minQuantity;
        String expiryDate;
        String unitName;
        double unitFactor;
        int isWeighted;

        ProductItem(int id, String name, String barcode, int categoryId, String categoryName,
                    double purchasePrice, double salePrice, double quantity, double minQuantity,
                    String expiryDate, String unitName, double unitFactor, int isWeighted) {
            this.id = id;
            this.name = name;
            this.barcode = barcode;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.purchasePrice = purchasePrice;
            this.salePrice = salePrice;
            this.quantity = quantity;
            this.minQuantity = minQuantity;
            this.expiryDate = expiryDate;
            this.unitName = unitName;
            this.unitFactor = unitFactor;
            this.isWeighted = isWeighted;
        }
    }

    private EditText etSearchProduct;
    private TextView tvProductsCount;
    private ListView lvProducts;
    private DatabaseHelper dbHelper;

    private ArrayList<ProductItem> productList;
    private ProductsAdapter adapter;

    private ArrayList<String> categoryList;
    private ArrayList<Integer> categoryIdList;

    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_products);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        etSearchProduct = findViewById(R.id.etSearchProduct);
        tvProductsCount = findViewById(R.id.tvProductsCount);
        lvProducts = findViewById(R.id.lvProducts);
        Button btnOpenAddProduct = findViewById(R.id.btnOpenAddProduct);
        View btnBack = findViewById(R.id.btnProductsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        productList = new ArrayList<>();
        categoryList = new ArrayList<>();
        categoryIdList = new ArrayList<>();

        adapter = new ProductsAdapter(this, productList, new ProductActionListener() {
            @Override
            public void onEdit(ProductItem product) {
                showProductDialog(product);
            }

            @Override
            public void onDelete(ProductItem product) {
                confirmDeleteProduct(product);
            }

            @Override
            public void onStockAdjust(ProductItem product) {
                showStockAdjustDialog(product);
            }
        });
        lvProducts.setAdapter(adapter);

        loadCategories();

        String initialQuery = getIntent().getStringExtra("SEARCH_QUERY");
        if (initialQuery != null && !initialQuery.isEmpty()) {
            etSearchProduct.setText(initialQuery);
            currentSearchQuery = initialQuery;
        }

        loadProducts(currentSearchQuery);

        btnOpenAddProduct.setOnClickListener(v -> showProductDialog(null));

        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim();
                loadProducts(currentSearchQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        lvProducts.setOnItemClickListener((parent, view, position, id) -> {
            ProductItem product = productList.get(position);
            showProductDialog(product);
        });

        BottomNavHelper.setupBottomNav(this, "products");

        if (getIntent().getBooleanExtra("OPEN_ADD_DIALOG", false)) {
            getIntent().removeExtra("OPEN_ADD_DIALOG");
            showProductDialog(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
        loadProducts(currentSearchQuery);
    }

    private void loadCategories() {
        categoryList.clear();
        categoryIdList.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, name FROM categories ORDER BY name", null);

        while (cursor.moveToNext()) {
            categoryIdList.add(cursor.getInt(0));
            categoryList.add(cursor.getString(1));
        }
        cursor.close();
    }

    private void loadProducts(String keyword) {
        productList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT p.id, p.name, p.barcode, p.category_id, c.name AS cat_name, " +
                "p.purchase_price, p.sale_price, p.quantity, p.min_quantity, p.expiry_date, " +
                "p.unit_name, p.unit_factor, p.is_weighted " +
                "FROM products p LEFT JOIN categories c ON p.category_id = c.id " +
                "WHERE (p.is_archived IS NULL OR p.is_archived = 0) ";

        Cursor cursor;
        if (keyword.isEmpty()) {
            sql += "ORDER BY p.id DESC";
            cursor = db.rawQuery(sql, null);
        } else {
            sql += "AND (p.name LIKE ? OR p.barcode LIKE ?) ORDER BY p.id DESC";
            cursor = db.rawQuery(sql, new String[]{"%" + keyword + "%", "%" + keyword + "%"});
        }

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(0);
                String name = cursor.getString(1);
                String barcode = cursor.getString(2);
                int catId = cursor.getInt(3);
                String catName = cursor.getString(4);
                if (catName == null || catName.isEmpty()) {
                    catName = "بدون تصنيف";
                }
                double purchasePrice = cursor.getDouble(5);
                double salePrice = cursor.getDouble(6);
                double qty = cursor.getDouble(7);
                double minQty = cursor.getDouble(8);
                String expiry = cursor.getString(9);
                String unit = cursor.getString(10);
                double factor = cursor.getDouble(11);
                int weighted = cursor.getInt(12);

                productList.add(new ProductItem(id, name, barcode, catId, catName,
                        purchasePrice, salePrice, qty, minQty, expiry, unit, factor, weighted));
            } while (cursor.moveToNext());
        }
        cursor.close();

        tvProductsCount.setText("إجمالي المنتجات النشطة: " + productList.size());
        adapter.notifyDataSetChanged();
    }

    private void showProductDialog(ProductItem existingProduct) {
        if (categoryIdList.isEmpty()) {
            Toast.makeText(this, "الرجاء إضافة تصنيف أولاً من شاشة التصنيفات!", Toast.LENGTH_LONG).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_product, null);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText dialogEtName = dialogView.findViewById(R.id.dialogEtProductName);
        EditText dialogEtBarcode = dialogView.findViewById(R.id.dialogEtProductBarcode);
        Button dialogBtnScanBarcode = dialogView.findViewById(R.id.dialogBtnScanBarcode);
        Spinner dialogSpCategory = dialogView.findViewById(R.id.dialogSpCategory);
        EditText dialogEtPurchasePrice = dialogView.findViewById(R.id.dialogEtPurchasePrice);
        EditText dialogEtSalePrice = dialogView.findViewById(R.id.dialogEtSalePrice);
        EditText dialogEtQuantity = dialogView.findViewById(R.id.dialogEtQuantity);
        EditText dialogEtMinQuantity = dialogView.findViewById(R.id.dialogEtMinQuantity);
        EditText dialogEtExpiryDate = dialogView.findViewById(R.id.dialogEtExpiryDate);
        EditText dialogEtUnitName = dialogView.findViewById(R.id.dialogEtUnitName);
        EditText dialogEtUnitFactor = dialogView.findViewById(R.id.dialogEtUnitFactor);
        CheckBox dialogCbIsWeighted = dialogView.findViewById(R.id.dialogCbIsWeighted);

        Button dialogBtnPickDate = dialogView.findViewById(R.id.dialogBtnPickDate);
        Button dialogBtnSave = dialogView.findViewById(R.id.dialogBtnSave);
        Button dialogBtnCancel = dialogView.findViewById(R.id.dialogBtnCancel);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                categoryList
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogSpCategory.setAdapter(categoryAdapter);

        boolean isEditMode = (existingProduct != null);
        if (isEditMode) {
            tvDialogTitle.setText("تعديل بيانات المنتج");
            dialogEtName.setText(existingProduct.name);
            dialogEtBarcode.setText(existingProduct.barcode != null ? existingProduct.barcode : "");
            dialogEtPurchasePrice.setText(String.valueOf(existingProduct.purchasePrice));
            dialogEtSalePrice.setText(String.valueOf(existingProduct.salePrice));
            dialogEtQuantity.setText(String.valueOf(existingProduct.quantity));
            dialogEtMinQuantity.setText(String.valueOf(existingProduct.minQuantity));
            dialogEtExpiryDate.setText(existingProduct.expiryDate != null ? existingProduct.expiryDate : "");
            dialogEtUnitName.setText(existingProduct.unitName);
            dialogEtUnitFactor.setText(String.valueOf(existingProduct.unitFactor));
            dialogCbIsWeighted.setChecked(existingProduct.isWeighted == 1);

            for (int i = 0; i < categoryIdList.size(); i++) {
                if (categoryIdList.get(i) == existingProduct.categoryId) {
                    dialogSpCategory.setSelection(i);
                    break;
                }
            }
            dialogBtnSave.setText("💾 تحديث المنتج");
        } else {
            tvDialogTitle.setText("إضافة منتج جديد");
            dialogEtMinQuantity.setText("5");
            dialogBtnSave.setText("💾 حفظ المنتج");
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogBtnScanBarcode.setOnClickListener(v -> {
            GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                    .enableAutoZoom()
                    .build();
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);

            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String code = barcode.getRawValue();
                        if (code != null && !code.isEmpty()) {
                            dialogEtBarcode.setText(code);
                            Toast.makeText(this, "تم مسح الباركود: " + code, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "تعذر مسح الباركود: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        dialogBtnPickDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(this, (view, y, m, d) -> {
                String formattedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                dialogEtExpiryDate.setText(formattedDate);
            }, year, month, day);
            datePicker.show();
        });

        dialogBtnSave.setOnClickListener(v -> {
            String name = dialogEtName.getText().toString().trim();
            String barcode = dialogEtBarcode.getText().toString().trim();
            String pPriceStr = dialogEtPurchasePrice.getText().toString().trim();
            String sPriceStr = dialogEtSalePrice.getText().toString().trim();
            String qtyStr = dialogEtQuantity.getText().toString().trim();
            String minQtyStr = dialogEtMinQuantity.getText().toString().trim();
            String expiry = dialogEtExpiryDate.getText().toString().trim();
            String unitName = dialogEtUnitName.getText().toString().trim();
            String factorStr = dialogEtUnitFactor.getText().toString().trim();
            int isWeighted = dialogCbIsWeighted.isChecked() ? 1 : 0;

            if (name.isEmpty() || pPriceStr.isEmpty() || sPriceStr.isEmpty() || qtyStr.isEmpty()) {
                Toast.makeText(this, "يرجى ملء الحقول الأساسية المطلوبة (*)", Toast.LENGTH_SHORT).show();
                return;
            }

            double purchasePrice;
            double salePrice;
            double quantity;
            double minQuantity;
            double unitFactor;

            try {
                purchasePrice = Double.parseDouble(pPriceStr);
                salePrice = Double.parseDouble(sPriceStr);
                quantity = Double.parseDouble(qtyStr);
                minQuantity = minQtyStr.isEmpty() ? 5.0 : Double.parseDouble(minQtyStr);
                unitFactor = factorStr.isEmpty() ? 1.0 : Double.parseDouble(factorStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "الرجاء إدخال أرقام صحيحة للأسعار والكمية", Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedCategoryIndex = dialogSpCategory.getSelectedItemPosition();
            if (selectedCategoryIndex < 0 || selectedCategoryIndex >= categoryIdList.size()) {
                Toast.makeText(this, "الرجاء اختيار تصنيف صالح", Toast.LENGTH_SHORT).show();
                return;
            }
            int categoryId = categoryIdList.get(selectedCategoryIndex);

            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // فحص عدم تكرار الباركود
            if (!barcode.isEmpty()) {
                int currentId = isEditMode ? existingProduct.id : -1;
                Cursor checkCursor = db.rawQuery(
                        "SELECT id FROM products WHERE barcode = ? AND (is_archived IS NULL OR is_archived = 0) AND id != ?",
                        new String[]{barcode, String.valueOf(currentId)}
                );
                if (checkCursor.moveToFirst()) {
                    checkCursor.close();
                    Toast.makeText(this, "عفواً! هذا الباركود مكرر ومُسجل لمنتج آخر بالفعل", Toast.LENGTH_LONG).show();
                    return;
                }
                checkCursor.close();
            }

            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("barcode", barcode);
            values.put("category_id", categoryId);
            values.put("purchase_price", purchasePrice);
            values.put("sale_price", salePrice);
            values.put("quantity", quantity);
            values.put("min_quantity", minQuantity);
            values.put("expiry_date", expiry);
            values.put("unit_name", unitName.isEmpty() ? "حبة" : unitName);
            values.put("unit_factor", unitFactor);
            values.put("is_weighted", isWeighted);
            values.put("alert_days", 20);

            if (!isEditMode) {
                long newId = db.insert("products", null, values);
                if (newId != -1) {
                    Toast.makeText(this, "✅ تم إضافة المنتج بنجاح!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadProducts(currentSearchQuery);
                } else {
                    Toast.makeText(this, "❌ فشل إضافة المنتج", Toast.LENGTH_SHORT).show();
                }
            } else {
                int affectedRows = db.update("products", values, "id = ?", new String[]{String.valueOf(existingProduct.id)});
                if (affectedRows > 0) {
                    Toast.makeText(this, "✅ تم تحديث المنتج بنجاح!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadProducts(currentSearchQuery);
                } else {
                    Toast.makeText(this, "❌ فشل تحديث المنتج", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialogBtnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showStockAdjustDialog(ProductItem product) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_simple, null);
        EditText etActualQty = view.findViewById(R.id.etSimpleName);
        EditText etNotes = view.findViewById(R.id.etSimplePhone);

        etActualQty.setHint("الكمية الفعلية الموجودة بالفحص *");
        etActualQty.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etActualQty.setText(String.valueOf(product.quantity));

        etNotes.setHint("سبب التسوية (تلف / عجز جرد / زيادة / منتهي)");
        etNotes.setInputType(InputType.TYPE_CLASS_TEXT);

        new MaterialAlertDialogBuilder(this)
                .setTitle("📋 تسوية الجرد للمنتج: " + product.name)
                .setMessage("الكمية المسجلة حالياً بالسيستم: " + product.quantity + " " + product.unitName)
                .setView(view)
                .setPositiveButton("حفظ تسوية الجرد", (dialog, which) -> {
                    String qtyStr = etActualQty.getText().toString().trim();
                    String notesStr = etNotes.getText().toString().trim();

                    if (qtyStr.isEmpty()) {
                        Toast.makeText(this, "أدخل الكمية الفعلية للجرد", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double actualQty;
                    try {
                        actualQty = Double.parseDouble(qtyStr);
                        if (actualQty < 0) {
                            Toast.makeText(this, "الكمية الفعلية لا يمكن أن تكون سالبة", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "كمية غير صالحة", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double diff = actualQty - product.quantity;

                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.beginTransaction();
                    try {
                        ContentValues cvAdj = new ContentValues();
                        cvAdj.put("product_id", product.id);
                        cvAdj.put("product_name", product.name);
                        cvAdj.put("system_quantity", product.quantity);
                        cvAdj.put("actual_quantity", actualQty);
                        cvAdj.put("difference", diff);
                        cvAdj.put("notes", notesStr.isEmpty() ? "تعديل جرد يدوي" : notesStr);
                        db.insert("stock_adjustments", null, cvAdj);

                        ContentValues cvProd = new ContentValues();
                        cvProd.put("quantity", actualQty);
                        db.update("products", cvProd, "id = ?", new String[]{String.valueOf(product.id)});

                        db.setTransactionSuccessful();
                        Toast.makeText(this, "✅ تم تسجيل تسوية الجرد وتحديث المخزون بنجاح (الفرق: " + String.format(Locale.US, "%.2f", diff) + ")", Toast.LENGTH_LONG).show();
                        loadProducts(currentSearchQuery);
                    } catch (Exception e) {
                        Toast.makeText(this, "خطأ في تسجيل تسوية الجرد: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } finally {
                        db.endTransaction();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void confirmDeleteProduct(ProductItem product) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // التحقق مما إذا كان للمنتج أي سجلات فواتير مبيعات أو مشتريات تاريخية
        Cursor cSale = db.rawQuery("SELECT COUNT(*) FROM sale_items WHERE product_id = ?", new String[]{String.valueOf(product.id)});
        int salesCount = 0;
        if (cSale.moveToFirst()) salesCount = cSale.getInt(0);
        cSale.close();

        Cursor cPurch = db.rawQuery("SELECT COUNT(*) FROM purchase_items WHERE product_id = ?", new String[]{String.valueOf(product.id)});
        int purchasesCount = 0;
        if (cPurch.moveToFirst()) purchasesCount = cPurch.getInt(0);
        cPurch.close();

        boolean hasHistory = (salesCount + purchasesCount) > 0;
        String actionTitle = hasHistory ? "أرشفة المنتج" : "حذف المنتج";
        String actionMessage = hasHistory ?
                "هذا المنتج مرطب بالفواتير وسجلات المبيعات والمشتريات (" + (salesCount + purchasesCount) + " عملية).\nسيتم أرشيف المنتج بحيث يختفي من قائمة البيع دون حذف الفواتير القديمة." :
                "هل أنت متأكد من حذف المنتج \"" + product.name + "\" نهائياً؟";

        new MaterialAlertDialogBuilder(this)
                .setTitle(actionTitle)
                .setMessage(actionMessage)
                .setPositiveButton(hasHistory ? "تأكيد الأرشفة" : "نعم، حذف", (dialog, which) -> {
                    SQLiteDatabase wDb = dbHelper.getWritableDatabase();
                    if (hasHistory) {
                        ContentValues cv = new ContentValues();
                        cv.put("is_archived", 1);
                        int updated = wDb.update("products", cv, "id = ?", new String[]{String.valueOf(product.id)});
                        if (updated > 0) {
                            Toast.makeText(this, "📦 تم أرشفة المنتج بنجاح مع الحفاظ على الفواتير والتقارير التاريخية", Toast.LENGTH_LONG).show();
                            loadProducts(currentSearchQuery);
                        } else {
                            Toast.makeText(this, "تعذر أرشفة المنتج", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        int deleted = wDb.delete("products", "id = ?", new String[]{String.valueOf(product.id)});
                        if (deleted > 0) {
                            Toast.makeText(this, "تم حذف المنتج بنجاح", Toast.LENGTH_SHORT).show();
                            loadProducts(currentSearchQuery);
                        } else {
                            Toast.makeText(this, "تعذر حذف المنتج", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    public interface ProductActionListener {
        void onEdit(ProductItem product);
        void onDelete(ProductItem product);
        void onStockAdjust(ProductItem product);
    }

    private static class ProductsAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<ProductItem> items;
        private final LayoutInflater inflater;
        private final ProductActionListener actionListener;

        ProductsAdapter(Context context, ArrayList<ProductItem> items, ProductActionListener listener) {
            this.context = context;
            this.items = items;
            this.actionListener = listener;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_product_card, parent, false);
                holder = new ViewHolder();
                holder.tvProductName = convertView.findViewById(R.id.tvProductName);
                holder.tvStockBadge = convertView.findViewById(R.id.tvStockBadge);
                holder.tvCategory = convertView.findViewById(R.id.tvCategory);
                holder.tvBarcode = convertView.findViewById(R.id.tvBarcode);
                holder.tvSalePrice = convertView.findViewById(R.id.tvSalePrice);
                holder.tvPurchasePrice = convertView.findViewById(R.id.tvPurchasePrice);
                holder.tvQuantity = convertView.findViewById(R.id.tvQuantity);
                holder.tvExpiryDate = convertView.findViewById(R.id.tvExpiryDate);
                holder.btnAdjust = convertView.findViewById(R.id.btnItemAdjust);
                holder.btnEdit = convertView.findViewById(R.id.btnItemEdit);
                holder.btnDelete = convertView.findViewById(R.id.btnItemDelete);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ProductItem item = items.get(position);

            holder.tvProductName.setText(item.name);
            holder.tvCategory.setText(item.categoryName);
            holder.tvBarcode.setText(item.barcode != null && !item.barcode.isEmpty() ? "الباركود: " + item.barcode : "بدون باركود");

            holder.tvSalePrice.setText("سعر البيع: " + item.salePrice + " ر.س");
            holder.tvPurchasePrice.setText("سعر الشراء: " + item.purchasePrice + " ر.س");
            holder.tvQuantity.setText("الكمية: " + item.quantity + " " + item.unitName);

            if (item.expiryDate != null && !item.expiryDate.isEmpty()) {
                holder.tvExpiryDate.setText("الانتهاء: " + item.expiryDate);
                holder.tvExpiryDate.setVisibility(View.VISIBLE);
            } else {
                holder.tvExpiryDate.setVisibility(View.GONE);
            }

            if (item.quantity <= 0) {
                holder.tvStockBadge.setText("نفد المخزون");
                holder.tvStockBadge.setTextColor(ContextCompat.getColor(context, R.color.danger));
                holder.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else if (item.quantity <= item.minQuantity) {
                holder.tvStockBadge.setText("مخزون منخفض (" + item.quantity + ")");
                holder.tvStockBadge.setTextColor(ContextCompat.getColor(context, R.color.warning));
                holder.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
            } else {
                holder.tvStockBadge.setText("متوفر (" + item.quantity + ")");
                holder.tvStockBadge.setTextColor(ContextCompat.getColor(context, R.color.success));
                holder.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
            }

            if (holder.btnAdjust != null) {
                holder.btnAdjust.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onStockAdjust(item);
                    }
                });
            }

            holder.btnEdit.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onEdit(item);
                }
            });

            holder.btnDelete.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDelete(item);
                }
            });

            return convertView;
        }

        private static class ViewHolder {
            TextView tvProductName;
            TextView tvStockBadge;
            TextView tvCategory;
            TextView tvBarcode;
            TextView tvSalePrice;
            TextView tvPurchasePrice;
            TextView tvQuantity;
            TextView tvExpiryDate;
            Button btnAdjust;
            Button btnEdit;
            Button btnDelete;
        }
    }
}
