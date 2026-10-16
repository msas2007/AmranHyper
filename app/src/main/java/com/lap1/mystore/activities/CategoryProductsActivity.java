package com.lap1.mystore.activities;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.Locale;

public class CategoryProductsActivity extends AppCompatActivity {

    private ListView lvProducts;
    private TextView tvTitle, tvCount;
    private DatabaseHelper dbHelper;
    private ArrayList<CategoryProductModel> productList;
    private CategoryProductsAdapter adapter;

    public static class CategoryProductModel {
        int id;
        String name;
        String barcode;
        double salePrice;
        double purchasePrice;
        int quantity;
        int minQuantity;
        String expiry;

        CategoryProductModel(int id, String name, String barcode, double salePrice, double purchasePrice, int quantity, int minQuantity, String expiry) {
            this.id = id;
            this.name = name;
            this.barcode = barcode;
            this.salePrice = salePrice;
            this.purchasePrice = purchasePrice;
            this.quantity = quantity;
            this.minQuantity = minQuantity;
            this.expiry = expiry;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_products);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        lvProducts = findViewById(R.id.lvProducts);
        tvTitle = findViewById(R.id.tvCategoryProductsTitle);
        tvCount = findViewById(R.id.tvCategoryProductsCount);
        View btnBack = findViewById(R.id.btnCategoryProductsBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        productList = new ArrayList<>();

        int categoryId = getIntent().getIntExtra("category_id", -1);
        String categoryName = getIntent().getStringExtra("category_name");

        if (tvTitle != null && categoryName != null) {
            tvTitle.setText("🏷️ تصنيف: " + categoryName);
        }

        loadProducts(categoryId);

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "more");
    }

    private void loadProducts(int categoryId) {
        productList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id, name, barcode, sale_price, purchase_price, quantity, min_quantity, expiry_date " +
                        "FROM products WHERE category_id = ? ORDER BY name",
                new String[]{String.valueOf(categoryId)}
        );

        while (cursor.moveToNext()) {
            productList.add(new CategoryProductModel(
                    cursor.getInt(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getDouble(3),
                    cursor.getDouble(4),
                    cursor.getInt(5),
                    cursor.getInt(6),
                    cursor.getString(7)
            ));
        }
        cursor.close();

        if (tvCount != null) {
            tvCount.setText("الأصناف المتوفرة (" + productList.size() + "):");
        }

        if (productList.isEmpty()) {
            Toast.makeText(this, "لا توجد منتجات مسجلة في هذا التصنيف حالياً", Toast.LENGTH_SHORT).show();
        }

        adapter = new CategoryProductsAdapter(this, productList);
        lvProducts.setAdapter(adapter);
    }

    private static class CategoryProductsAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<CategoryProductModel> list;

        CategoryProductsAdapter(Context context, ArrayList<CategoryProductModel> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return list.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_product_card, parent, false);
            }

            TextView tvName = convertView.findViewById(R.id.tvProductName);
            TextView tvStockBadge = convertView.findViewById(R.id.tvStockBadge);
            TextView tvBarcode = convertView.findViewById(R.id.tvBarcode);
            TextView tvSalePrice = convertView.findViewById(R.id.tvSalePrice);
            TextView tvPurchasePrice = convertView.findViewById(R.id.tvPurchasePrice);
            TextView tvQuantity = convertView.findViewById(R.id.tvQuantity);
            TextView tvExpiryDate = convertView.findViewById(R.id.tvExpiryDate);
            View layoutActions = convertView.findViewById(R.id.btnItemEdit).getParent() instanceof View ? (View) convertView.findViewById(R.id.btnItemEdit).getParent() : null;

            if (layoutActions != null) {
                layoutActions.setVisibility(View.GONE);
            }

            CategoryProductModel p = list.get(position);
            tvName.setText(p.name);
            tvBarcode.setText("الباركود: " + (p.barcode != null && !p.barcode.isEmpty() ? p.barcode : "--"));
            tvSalePrice.setText(String.format(Locale.US, "%.2f ر.س", p.salePrice));
            tvPurchasePrice.setText(String.format(Locale.US, "شراء: %.2f ر.س", p.purchasePrice));
            tvQuantity.setText("المخزون: " + p.quantity);
            tvExpiryDate.setText(p.expiry != null && !p.expiry.isEmpty() ? "الانتهاء: " + p.expiry : "لا يوجد تاريخ");

            if (p.quantity <= 0) {
                tvStockBadge.setText("نافد");
                tvStockBadge.setTextColor(context.getResources().getColor(R.color.danger));
                tvStockBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else if (p.quantity <= p.minQuantity) {
                tvStockBadge.setText("منخفض");
                tvStockBadge.setTextColor(context.getResources().getColor(R.color.warning));
                tvStockBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
            } else {
                tvStockBadge.setText("متوفر");
                tvStockBadge.setTextColor(context.getResources().getColor(R.color.success));
                tvStockBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
            }

            return convertView;
        }
    }
}
