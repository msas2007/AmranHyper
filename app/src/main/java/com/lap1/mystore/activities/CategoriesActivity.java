package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;

public class CategoriesActivity extends AppCompatActivity {

    private EditText etCategoryName;
    private Button btnAddCategory;
    private ListView lvCategories;
    private TextView tvCategoriesCount;

    private DatabaseHelper dbHelper;
    private ArrayList<CategoryModel> categoryList;
    private CategoryAdapter adapter;

    public static class CategoryModel {
        int id;
        String name;

        CategoryModel(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etCategoryName = findViewById(R.id.etCategoryName);
        btnAddCategory = findViewById(R.id.btnAddCategory);
        lvCategories = findViewById(R.id.lvCategories);
        tvCategoriesCount = findViewById(R.id.tvCategoriesCount);
        View btnBack = findViewById(R.id.btnCategoriesBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        categoryList = new ArrayList<>();

        loadCategories();

        btnAddCategory.setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(CategoriesActivity.this, "الرجاء إدخال اسم التصنيف", Toast.LENGTH_SHORT).show();
                return;
            }

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("name", name);

            long id = db.insert("categories", null, values);

            if (id != -1) {
                Toast.makeText(CategoriesActivity.this, "تم إضافة التصنيف بنجاح", Toast.LENGTH_SHORT).show();
                etCategoryName.setText("");
                loadCategories();
            } else {
                Toast.makeText(CategoriesActivity.this, "فشل في إضافة التصنيف", Toast.LENGTH_SHORT).show();
            }
        });

        lvCategories.setOnItemClickListener((parent, view, position, id) -> {
            CategoryModel item = categoryList.get(position);
            Intent intent = new Intent(CategoriesActivity.this, CategoryProductsActivity.class);
            intent.putExtra("category_id", item.id);
            intent.putExtra("category_name", item.name);
            startActivity(intent);
        });

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "more");
    }

    private void loadCategories() {
        categoryList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, name FROM categories ORDER BY name", null);

        while (cursor.moveToNext()) {
            categoryList.add(new CategoryModel(cursor.getInt(0), cursor.getString(1)));
        }
        cursor.close();

        if (tvCategoriesCount != null) {
            tvCategoriesCount.setText("قائمة التصنيفات المتوفرة (" + categoryList.size() + "):");
        }

        adapter = new CategoryAdapter(this, categoryList);
        lvCategories.setAdapter(adapter);
    }

    private static class CategoryAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<CategoryModel> list;

        CategoryAdapter(Context context, ArrayList<CategoryModel> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return list.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_category_row, parent, false);
            }

            TextView tvName = convertView.findViewById(R.id.tvCategoryItemName);
            CategoryModel item = list.get(position);
            tvName.setText(item.name);

            return convertView;
        }
    }
}