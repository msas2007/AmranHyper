package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.util.ArrayList;
import java.util.Locale;

public class SalesActivity extends AppCompatActivity {

    private EditText etProductBarcode, etSaleQuantity, etPaidAmount, etNewCustomerName, etNewCustomerPhone, etSaleDiscount;
    private TextView tvCartHeaderTitle, tvCartTotal, tvSaleResult, tvNetTotal;
    private ListView lvCartItems;
    private Spinner spinnerCustomers, spPaymentMethod;
    private LinearLayout layoutNewCustomer, layoutQuickProducts;
    private Switch switchCreditSale;
    private DatabaseHelper dbHelper;

    private ArrayList<CartItem> cartItems;
    private CartAdapter cartAdapter;
    private double grandTotal = 0.0;
    private double netTotal = 0.0;

    private ArrayList<String> customerNamesList;
    private ArrayList<Integer> customerIdsList;
    private ArrayAdapter<String> customerSpinnerAdapter;
    private int selectedCustomerId = -1;
    
    private String lastInvoiceNo = "";
    private double lastTotal = 0.0;
    private double lastPaid = 0.0;
    private double lastRemaining = 0.0;
    private String lastPaymentStatus = "";

    public static class CartItem {
        int productId;
        String productName;
        double quantity;
        double unitPrice;
        double purchasePrice;
        double total;

        CartItem(int productId, String productName, double quantity, double unitPrice, double purchasePrice, double total) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.purchasePrice = purchasePrice;
            this.total = total;
        }
    }

    private class CartAdapter extends ArrayAdapter<CartItem> {
        public CartAdapter(Context context, ArrayList<CartItem> items) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_cart_row, parent, false);
            }

            CartItem item = getItem(position);
            if (item == null) return convertView;

            TextView tvName = convertView.findViewById(R.id.tvCartProductName);
            TextView tvPrice = convertView.findViewById(R.id.tvCartProductPrice);
            TextView tvQty = convertView.findViewById(R.id.tvCartQty);
            TextView tvTotal = convertView.findViewById(R.id.tvCartItemTotal);
            ImageButton btnDec = convertView.findViewById(R.id.btnCartDecrement);
            ImageButton btnInc = convertView.findViewById(R.id.btnCartIncrement);
            ImageButton btnRemove = convertView.findViewById(R.id.btnCartRemove);

            tvName.setText(item.productName);
            tvPrice.setText(String.format(Locale.getDefault(), "%.2f ر.س للوحدة", item.unitPrice));
            tvQty.setText(String.format(Locale.getDefault(), "%.0f", item.quantity));
            tvTotal.setText(String.format(Locale.getDefault(), "%.2f ر.س", item.total));

            btnInc.setOnClickListener(v -> {
                double availableStock = getProductStock(item.productId);
                if (item.quantity + 1.0 > availableStock) {
                    Toast.makeText(SalesActivity.this, "عفواً! لا يمكن تجاوز المخزون المتاح (" + availableStock + ")", Toast.LENGTH_SHORT).show();
                    return;
                }
                item.quantity += 1.0;
                item.total = item.quantity * item.unitPrice;
                recalculateGrandTotal();
                notifyDataSetChanged();
            });

            btnDec.setOnClickListener(v -> {
                if (item.quantity > 1.0) {
                    item.quantity -= 1.0;
                    item.total = item.quantity * item.unitPrice;
                    recalculateGrandTotal();
                    notifyDataSetChanged();
                } else {
                    cartItems.remove(position);
                    recalculateGrandTotal();
                    notifyDataSetChanged();
                    Toast.makeText(SalesActivity.this, "تم حذف المنتج من السلة", Toast.LENGTH_SHORT).show();
                }
            });

            btnRemove.setOnClickListener(v -> {
                cartItems.remove(position);
                recalculateGrandTotal();
                notifyDataSetChanged();
                Toast.makeText(SalesActivity.this, "تم حذف المنتج من السلة", Toast.LENGTH_SHORT).show();
            });

            return convertView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        etProductBarcode = findViewById(R.id.etSaleProductBarcode);
        etSaleQuantity = findViewById(R.id.etSaleQuantity);
        etPaidAmount = findViewById(R.id.etPaidAmount);
        etNewCustomerName = findViewById(R.id.etNewCustomerName);
        etNewCustomerPhone = findViewById(R.id.etNewCustomerPhone);

        View btnSalesBack = findViewById(R.id.btnSalesBack);
        if (btnSalesBack != null) {
            btnSalesBack.setOnClickListener(v -> finish());
        }

        Button btnAddToCart = findViewById(R.id.btnAddToCart);
        Button btnCompleteSale = findViewById(R.id.btnCompleteSale);
        Button btnScanBarcode = findViewById(R.id.btnScanBarcode);
        Button btnShareInvoice = findViewById(R.id.btnShareInvoice);

        tvCartHeaderTitle = findViewById(R.id.tvCartHeaderTitle);
        tvCartTotal = findViewById(R.id.tvCartTotal);
        tvNetTotal = findViewById(R.id.tvNetTotal);
        tvSaleResult = findViewById(R.id.tvSaleResult);
        lvCartItems = findViewById(R.id.lvCartItems);
        etSaleDiscount = findViewById(R.id.etSaleDiscount);
        spinnerCustomers = findViewById(R.id.spinnerCustomers);
        spPaymentMethod = findViewById(R.id.spPaymentMethod);
        layoutNewCustomer = findViewById(R.id.layoutNewCustomer);
        layoutQuickProducts = findViewById(R.id.layoutQuickProducts);
        switchCreditSale = findViewById(R.id.switchCreditSale);

        dbHelper = new DatabaseHelper(this);
        cartItems = new ArrayList<>();

        BottomNavHelper.setupBottomNav(this, "pos");

        cartAdapter = new CartAdapter(this, cartItems);
        lvCartItems.setAdapter(cartAdapter);

        loadCustomersIntoSpinner();
        setupPaymentMethodSpinner();
        loadQuickProducts();

        etSaleDiscount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateNetTotal(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        switchCreditSale.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etPaidAmount.setVisibility(View.VISIBLE);
                spinnerCustomers.setVisibility(View.VISIBLE);
                Toast.makeText(SalesActivity.this, "تم تفعيل البيع الآجل/الجزئي", Toast.LENGTH_SHORT).show();
            } else {
                etPaidAmount.setVisibility(View.GONE);
                spinnerCustomers.setVisibility(View.GONE);
                layoutNewCustomer.setVisibility(View.GONE);
                etPaidAmount.setText("");
            }
        });

        btnScanBarcode.setOnClickListener(v -> {
            GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder().enableAutoZoom().build();
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);

            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String code = barcode.getRawValue();
                        if (code != null && !code.isEmpty()) {
                            etProductBarcode.setText(code);
                            Toast.makeText(SalesActivity.this, "تم مسح الباركود: " + code, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SalesActivity.this, "تعذر قراءة الباركود", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(SalesActivity.this, "فشل مسح الباركود: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });

        btnAddToCart.setOnClickListener(v -> addProductToCart());
        btnCompleteSale.setOnClickListener(v -> completeSale());
        btnShareInvoice.setOnClickListener(v -> shareInvoice());
    }

    private void recalculateGrandTotal() {
        grandTotal = 0.0;
        for (CartItem item : cartItems) {
            grandTotal += item.total;
        }
        if (tvCartHeaderTitle != null) {
            tvCartHeaderTitle.setText("🛒 السلة (" + cartItems.size() + " أصناف)");
        }
        tvCartTotal.setText(String.format(Locale.getDefault(), "%.2f ر.س", grandTotal));
        calculateNetTotal();
    }

    private void setupPaymentMethodSpinner() {
        String[] methods = {"كاش", "شبكة"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, methods);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPaymentMethod.setAdapter(adapter);
    }

    private void loadQuickProducts() {
        layoutQuickProducts.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, name, sale_price, barcode FROM products LIMIT 15", null);
        
        while (cursor.moveToNext()) {
            String name = cursor.getString(1);
            double price = cursor.getDouble(2);
            String barcode = cursor.getString(3);

            View card = LayoutInflater.from(this).inflate(R.layout.item_product_quick, layoutQuickProducts, false);
            TextView tvName = card.findViewById(R.id.tvQuickProductName);
            TextView tvPrice = card.findViewById(R.id.tvQuickProductPrice);

            tvName.setText(name);
            tvPrice.setText(String.format(Locale.getDefault(), "%.2f ر.س", price));

            card.setOnClickListener(v -> {
                etProductBarcode.setText(barcode != null && !barcode.isEmpty() ? barcode : name);
                addProductToCart();
            });

            layoutQuickProducts.addView(card);
        }
        cursor.close();
    }

    private void loadCustomersIntoSpinner() {
        customerNamesList = new ArrayList<>();
        customerIdsList = new ArrayList<>();

        customerNamesList.add("-- اختر عميل مسجل --");
        customerIdsList.add(-1);

        customerNamesList.add("+ إضافة عميل جديد...");
        customerIdsList.add(0);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, name FROM customers", null);
        if (cursor.moveToFirst()) {
            do {
                customerIdsList.add(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                customerNamesList.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            } while (cursor.moveToNext());
        }
        cursor.close();

        customerSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, customerNamesList);
        customerSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCustomers.setAdapter(customerSpinnerAdapter);

        spinnerCustomers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedId = customerIdsList.get(position);
                selectedCustomerId = selectedId;
                if (selectedId == 0) {
                    layoutNewCustomer.setVisibility(View.VISIBLE);
                } else {
                    layoutNewCustomer.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void calculateNetTotal() {
        String discStr = etSaleDiscount.getText().toString().trim();
        double discount = discStr.isEmpty() ? 0.0 : Double.parseDouble(discStr);
        netTotal = grandTotal - discount;
        if (netTotal < 0) netTotal = 0;
        tvNetTotal.setText("الصافي: " + String.format(Locale.getDefault(), "%.2f ر.س", netTotal));
    }

    private double getProductStock(int productId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        double stock = 0;
        Cursor cursor = db.rawQuery("SELECT quantity FROM products WHERE id = ?", new String[]{String.valueOf(productId)});
        if (cursor.moveToFirst()) {
            stock = cursor.getDouble(0);
        }
        cursor.close();
        return stock;
    }

    private void addProductToCart() {
        String queryText = etProductBarcode.getText().toString().trim();
        String qtyStr = etSaleQuantity.getText().toString().trim();

        if (queryText.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال باركود أو اسم المنتج", Toast.LENGTH_SHORT).show();
            return;
        }

        double requestedQty;
        try {
            requestedQty = qtyStr.isEmpty() ? 1.0 : Double.parseDouble(qtyStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "الكمية المُدخلة غير صالحة", Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestedQty <= 0) {
            Toast.makeText(this, "الكمية يجب أن تكون أكبر من الصفر", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        // Scale barcode parsing (starts with 22 and length 13)
        if (queryText.length() == 13 && queryText.startsWith("22")) {
            try {
                String skuStr = queryText.substring(2, 7);
                String weightStr = queryText.substring(7, 12);
                int productId = Integer.parseInt(skuStr);
                requestedQty = Double.parseDouble(weightStr) / 1000.0;
                cursor = db.rawQuery("SELECT * FROM products WHERE id = ?", new String[]{String.valueOf(productId)});
                Toast.makeText(this, "منتج ميزان (وزن: " + requestedQty + " كجم)", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        }

        if (cursor == null) {
            cursor = db.rawQuery("SELECT * FROM products WHERE (is_archived IS NULL OR is_archived = 0) AND (name = ? OR barcode = ?)", new String[]{queryText, queryText});
        }

        if (!cursor.moveToFirst()) {
            Toast.makeText(this, "المنتج غير موجود أو مؤرشف!", Toast.LENGTH_SHORT).show();
            cursor.close();
            return;
        }

        int productId = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
        String productName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        double salePrice = cursor.getDouble(cursor.getColumnIndexOrThrow("sale_price"));
        double purchasePrice = cursor.getDouble(cursor.getColumnIndexOrThrow("purchase_price"));
        double availableQty = cursor.getDouble(cursor.getColumnIndexOrThrow("quantity"));
        cursor.close();

        // حساب الكمية الحالية في السلة بنفس المنتج
        double currentInCart = 0.0;
        CartItem existingItem = null;
        for (CartItem item : cartItems) {
            if (item.productId == productId) {
                currentInCart = item.quantity;
                existingItem = item;
                break;
            }
        }

        double totalNewQty = currentInCart + requestedQty;
        if (totalNewQty > availableQty) {
            Toast.makeText(this, "الكمية المطلوبة الإجمالية (" + totalNewQty + ") تتجاوز المخزون المتاح (" + availableQty + ")!", Toast.LENGTH_LONG).show();
            return;
        }

        if (existingItem != null) {
            existingItem.quantity = totalNewQty;
            existingItem.total = existingItem.quantity * existingItem.unitPrice;
        } else {
            double itemTotal = salePrice * requestedQty;
            cartItems.add(new CartItem(productId, productName, requestedQty, salePrice, purchasePrice, itemTotal));
        }

        recalculateGrandTotal();
        cartAdapter.notifyDataSetChanged();

        etProductBarcode.setText("");
        etSaleQuantity.setText("1");
        Toast.makeText(this, "تمت إضافة " + productName + " للسلة", Toast.LENGTH_SHORT).show();
    }

    private void completeSale() {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "السلة فارغة!", Toast.LENGTH_SHORT).show();
            return;
        }

        double discount = 0;
        try {
            String dStr = etSaleDiscount.getText().toString().trim();
            if (!dStr.isEmpty()) discount = Double.parseDouble(dStr);
        } catch (Exception ignored) {}

        double paid;
        double remaining;
        String paymentStatus;

        boolean isCreditOrPartial = switchCreditSale.isChecked();

        if (!isCreditOrPartial) {
            paid = netTotal;
            remaining = 0.0;
            paymentStatus = "مدفوعة بالكامل";
        } else {
            String paidStr = etPaidAmount.getText().toString().trim();
            if (paidStr.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال المبلغ المدفوع", Toast.LENGTH_SHORT).show();
                return;
            }
            paid = Double.parseDouble(paidStr);
            if (paid > netTotal) {
                Toast.makeText(this, "المبلغ المدفوع أكبر من صافي الفاتورة!", Toast.LENGTH_SHORT).show();
                return;
            }
            remaining = netTotal - paid;
            paymentStatus = (remaining == 0) ? "مدفوعة بالكامل" : (paid > 0 ? "دفع جزئي" : "آجلة");
            
            if (selectedCustomerId == -1) {
                Toast.makeText(this, "يجب اختيار عميل للآجل/الجزئي!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedCustomerId == 0) {
                String newName = etNewCustomerName.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(this, "الرجاء أدخل اسم العميل الجديد للبيع الآجل/الجزئي", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        Button btnCompleteSale = findViewById(R.id.btnCompleteSale);
        if (btnCompleteSale != null) btnCompleteSale.setEnabled(false);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            long customerId = -1;
            if (isCreditOrPartial) {
                if (selectedCustomerId == 0) {
                    String newName = etNewCustomerName.getText().toString().trim();
                    ContentValues cvCust = new ContentValues();
                    cvCust.put("name", newName);
                    cvCust.put("phone", etNewCustomerPhone.getText().toString().trim());
                    cvCust.put("balance", remaining);
                    customerId = db.insert("customers", null, cvCust);
                } else {
                    customerId = selectedCustomerId;
                    if (remaining > 0) db.execSQL("UPDATE customers SET balance = balance + ? WHERE id = ?", new Object[]{remaining, customerId});
                }
            }

            lastInvoiceNo = "INV-" + System.currentTimeMillis();
            lastTotal = netTotal;
            lastPaid = paid;
            lastRemaining = remaining;
            lastPaymentStatus = paymentStatus;
            String pMethod = spPaymentMethod.getSelectedItem().toString();

            ContentValues cvSale = new ContentValues();
            cvSale.put("invoice_number", lastInvoiceNo);
            cvSale.put("customer_id", customerId != -1 ? customerId : null);
            cvSale.put("total", grandTotal);
            cvSale.put("discount", discount);
            cvSale.put("paid", lastPaid);
            cvSale.put("remaining", lastRemaining);
            cvSale.put("payment_status", lastPaymentStatus);
            cvSale.put("payment_method", pMethod);
            long saleId = db.insert("sales", null, cvSale);

            for (CartItem item : cartItems) {
                ContentValues cvItem = new ContentValues();
                cvItem.put("sale_id", saleId);
                cvItem.put("product_id", item.productId);
                cvItem.put("quantity", item.quantity);
                cvItem.put("unit_price", item.unitPrice);
                cvItem.put("purchase_price", item.purchasePrice);
                cvItem.put("total", item.total);
                db.insert("sale_items", null, cvItem);

                db.execSQL("UPDATE products SET quantity = quantity - ? WHERE id = ?", new Object[]{item.quantity, item.productId});
            }

            db.setTransactionSuccessful();
            Toast.makeText(this, "تم إتمام عملية البيع بنجاح!", Toast.LENGTH_LONG).show();
            tvSaleResult.setText("رقم الفاتورة: " + lastInvoiceNo + " | الصافي: " + String.format(Locale.getDefault(), "%.2f ر.س", lastTotal) + " | حالة الدفع: " + lastPaymentStatus);

            cartItems.clear();
            cartAdapter.notifyDataSetChanged();
            recalculateGrandTotal();
            etSaleDiscount.setText("");
            switchCreditSale.setChecked(false);
            etPaidAmount.setText("");
            etNewCustomerName.setText("");
            etNewCustomerPhone.setText("");
            loadCustomersIntoSpinner();

        } catch (Exception e) {
            Toast.makeText(this, "خطأ في عملية البيع: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
            if (btnCompleteSale != null) btnCompleteSale.setEnabled(true);
        }
    }

    private void shareInvoice() {
        if (lastInvoiceNo.isEmpty()) {
            Toast.makeText(this, "لم يتم إصدار فاتورة بعد لمشاركتها!", Toast.LENGTH_SHORT).show();
            return;
        }

        String invoiceDetails = "🛒 *عمران هيبر — فاتورة مبيعات*\n" +
                "------------------------------------\n" +
                "📄 رقم الفاتورة: " + lastInvoiceNo + "\n" +
                "💰 الصافي النهائي: " + String.format(Locale.getDefault(), "%.2f ر.س", lastTotal) + "\n" +
                "💵 المبلغ المدفوع: " + String.format(Locale.getDefault(), "%.2f ر.س", lastPaid) + "\n" +
                "⏳ المتبقي (دين): " + String.format(Locale.getDefault(), "%.2f ر.س", lastRemaining) + "\n" +
                "📌 حالة الدفع: " + lastPaymentStatus + "\n" +
                "------------------------------------\n" +
                "شكراً لتسوقكم معنا! 🙏";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "فاتورة مبيعات - عمران هيبر");
        shareIntent.putExtra(Intent.EXTRA_TEXT, invoiceDetails);
        startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة عبر"));
    }
}
