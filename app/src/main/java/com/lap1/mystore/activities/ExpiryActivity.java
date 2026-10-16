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

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ExpiryActivity extends AppCompatActivity {

    private ListView lvAlerts;
    private TextView tvAlertsCount;
    private DatabaseHelper dbHelper;
    private ArrayList<AlertModel> alertList;
    private AlertAdapter adapter;

    public enum AlertType {
        EXPIRED,
        NEAR_EXPIRY,
        OUT_OF_STOCK,
        LOW_STOCK,
        INFO
    }

    public static class AlertModel {
        String badge;
        AlertType type;
        String productName;
        String details;

        AlertModel(String badge, AlertType type, String productName, String details) {
            this.badge = badge;
            this.type = type;
            this.productName = productName;
            this.details = details;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expiry);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        lvAlerts = findViewById(R.id.lvExpiryAlerts);
        tvAlertsCount = findViewById(R.id.tvAlertsCount);
        View btnBack = findViewById(R.id.btnExpiryBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        alertList = new ArrayList<>();

        loadAllAlerts();

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "more");
    }

    private void loadAllAlerts() {
        alertList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        Calendar calToday = Calendar.getInstance();
        calToday.set(Calendar.HOUR_OF_DAY, 0);
        calToday.set(Calendar.MINUTE, 0);
        calToday.set(Calendar.SECOND, 0);
        calToday.set(Calendar.MILLISECOND, 0);
        Date today = calToday.getTime();

        Cursor cursor = db.rawQuery("SELECT name, quantity, min_quantity, expiry_date FROM products", null);

        if (cursor.moveToFirst()) {
            do {
                String name = cursor.getString(0);
                int quantity = cursor.getInt(1);
                int minQuantity = cursor.getInt(2);
                String expiryStr = cursor.getString(3);

                // 1. تنبيه نفاذ أو انخفاض المخزون
                if (quantity <= 0) {
                    alertList.add(new AlertModel("نافد المخزون", AlertType.OUT_OF_STOCK, name, "الكمية الحالية: 0 قطعة (بحاجة لإعادة طلب عاجلة)"));
                } else if (quantity <= minQuantity) {
                    alertList.add(new AlertModel("مخزون منخفض", AlertType.LOW_STOCK, name, "الكمية الحالية: " + quantity + " | الحد الأدنى للأمان: " + minQuantity));
                }

                // 2. تنبيه تواريخ الصلاحية
                if (expiryStr != null && !expiryStr.trim().isEmpty()) {
                    try {
                        String cleanDateStr = expiryStr.trim().replaceAll("/", "-");
                        Date expiryDate = sdf.parse(cleanDateStr);

                        if (expiryDate != null) {
                            Calendar calExpiry = Calendar.getInstance();
                            calExpiry.setTime(expiryDate);
                            calExpiry.set(Calendar.HOUR_OF_DAY, 0);
                            calExpiry.set(Calendar.MINUTE, 0);
                            calExpiry.set(Calendar.SECOND, 0);
                            calExpiry.set(Calendar.MILLISECOND, 0);

                            long diffInMillies = calExpiry.getTimeInMillis() - today.getTime();
                            long diffDays = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);

                            if (diffDays < 0) {
                                alertList.add(new AlertModel("منتهي الصلاحية", AlertType.EXPIRED, name, "❌ انتهت الصلاحية منذ " + Math.abs(diffDays) + " يوم (تاريخ الانتهاء: " + cleanDateStr + ")"));
                            } else if (diffDays <= 20) {
                                alertList.add(new AlertModel("قريب من الانتهاء", AlertType.NEAR_EXPIRY, name, "⚠️ ينتهي خلال " + diffDays + " يوم (تاريخ الانتهاء: " + cleanDateStr + ")"));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

            } while (cursor.moveToNext());
        }
        cursor.close();

        if (tvAlertsCount != null) {
            tvAlertsCount.setText(alertList.size() + " تنبيه");
            if (alertList.isEmpty()) {
                tvAlertsCount.setBackgroundResource(R.drawable.bg_badge_good_stock);
                tvAlertsCount.setTextColor(getResources().getColor(R.color.success));
                tvAlertsCount.setText("لا توجد تنبيهات");
            }
        }

        if (alertList.isEmpty()) {
            alertList.add(new AlertModel("حالة ممتازة", AlertType.INFO, "جميع المنتجات بحالة جيدة", "لا توجد أي منتجات منتهية الصلاحية أو نواقص مخزون حرجة حالياً."));
        }

        adapter = new AlertAdapter(this, alertList);
        lvAlerts.setAdapter(adapter);
    }

    private static class AlertAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<AlertModel> list;

        AlertAdapter(Context context, ArrayList<AlertModel> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_alert_card, parent, false);
            }

            TextView tvBadge = convertView.findViewById(R.id.tvAlertBadge);
            TextView tvTitle = convertView.findViewById(R.id.tvAlertProductName);
            TextView tvDetails = convertView.findViewById(R.id.tvAlertDetails);

            AlertModel item = list.get(position);
            tvBadge.setText(item.badge);
            tvTitle.setText(item.productName);
            tvDetails.setText(item.details);

            switch (item.type) {
                case EXPIRED:
                case OUT_OF_STOCK:
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
                    tvBadge.setTextColor(context.getResources().getColor(R.color.danger));
                    break;
                case NEAR_EXPIRY:
                case LOW_STOCK:
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
                    tvBadge.setTextColor(context.getResources().getColor(R.color.warning));
                    break;
                case INFO:
                default:
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
                    tvBadge.setTextColor(context.getResources().getColor(R.color.success));
                    break;
            }

            return convertView;
        }
    }
}
