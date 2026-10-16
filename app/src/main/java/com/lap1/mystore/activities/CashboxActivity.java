package com.lap1.mystore.activities;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CashboxActivity — يعرض الحركات النقدية المستخرجة مباشرة من جداول المعاملات الأصلية.
 * لا ينشئ نظاماً ثانياً ولا يكرر الحركات.
 */
public class CashboxActivity extends AppCompatActivity {

    private TextView tvCashIn, tvCashOut, tvNetCash, tvNetworkIn, tvNetworkOut, tvDate;
    private LinearLayout layoutMovements;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashbox);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        tvCashIn = findViewById(R.id.tvCashboxCashIn);
        tvCashOut = findViewById(R.id.tvCashboxCashOut);
        tvNetCash = findViewById(R.id.tvCashboxNetCash);
        tvNetworkIn = findViewById(R.id.tvCashboxNetworkIn);
        tvNetworkOut = findViewById(R.id.tvCashboxNetworkOut);
        tvDate = findViewById(R.id.tvCashboxDate);
        layoutMovements = findViewById(R.id.layoutCashboxMovements);
        View btnBack = findViewById(R.id.btnCashboxBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        Button btnToday = findViewById(R.id.btnCashboxToday);
        Button btnWeek = findViewById(R.id.btnCashboxWeek);
        if (btnToday != null) btnToday.setOnClickListener(v -> loadForToday());
        if (btnWeek != null) btnWeek.setOnClickListener(v -> loadForWeek());

        dbHelper = new DatabaseHelper(this);
        loadForToday();
        BottomNavHelper.setupBottomNav(this, "more");
    }

    private void loadForToday() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (tvDate != null) tvDate.setText("📅 اليوم: " + today);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        double cashIn = dbHelper.getCashInForDate(db, today);
        double cashOut = dbHelper.getCashOutForDate(db, today);
        double networkIn = dbHelper.getNetworkInForDate(db, today);
        double networkOut = dbHelper.getNetworkOutForDate(db, today);
        showTotals(cashIn, cashOut, networkIn, networkOut);
        loadMovementsForDate(db, today);
    }

    private void loadForWeek() {
        if (tvDate != null) tvDate.setText("📅 آخر 7 أيام");
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        double cashIn = dbHelper.getCashInForDateRange(db, "WEEK");
        double cashOut = dbHelper.getCashOutForDateRange(db, "WEEK");
        double networkIn = dbHelper.getNetworkInForDateRange(db, "WEEK");
        double networkOut = dbHelper.getNetworkOutForDateRange(db, "WEEK");
        showTotals(cashIn, cashOut, networkIn, networkOut);
        if (layoutMovements != null) {
            layoutMovements.removeAllViews();
            addMovementRow(layoutMovements, "📊 إجمالي 7 أيام", "الكاش الوارد: " + fmt(cashIn) + " | المصروف: " + fmt(cashOut), true);
        }
    }

    private void showTotals(double cashIn, double cashOut, double netIn, double netOut) {
        if (tvCashIn != null) tvCashIn.setText("⬇ وارد نقدي: " + fmt(cashIn) + " ر.ي");
        if (tvCashOut != null) tvCashOut.setText("⬆ صادر نقدي: " + fmt(cashOut) + " ر.ي");
        if (tvNetworkIn != null) tvNetworkIn.setText("🏧 وارد شبكة: " + fmt(netIn) + " ر.ي");
        if (tvNetworkOut != null) tvNetworkOut.setText("🏧 صادر شبكة: " + fmt(netOut) + " ر.ي");
        double net = cashIn - cashOut;
        if (tvNetCash != null) {
            tvNetCash.setText("💰 صافي النقد: " + fmt(net) + " ر.ي");
            tvNetCash.setTextColor(ContextCompat.getColor(this, net >= 0 ? R.color.success : R.color.danger));
        }
    }

    private void loadMovementsForDate(SQLiteDatabase db, String date) {
        if (layoutMovements == null) return;
        layoutMovements.removeAllViews();

        // 1. مبيعات نقدية
        double salesCash = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM sales WHERE date(sale_date,'localtime')=? AND payment_method='كاش'", date);
        if (salesCash > 0.001) addMovementRow(layoutMovements, "🛒 مبيعات نقدية", fmt(salesCash) + " ر.ي (وارد)", true);

        // 2. سداد عملاء نقدي
        double pmtCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE date(payment_date,'localtime')=? AND COALESCE(payment_method,'كاش')='كاش'", date);
        if (pmtCash > 0.001) addMovementRow(layoutMovements, "👤 سداد عملاء (نقدي)", fmt(pmtCash) + " ر.ي (وارد)", true);

        // 3. مرتجع مشتريات — نقد للمحل
        double prRefund = safeScalar(db, "SELECT COALESCE(SUM(cash_refund),0) FROM purchase_returns WHERE date(return_date,'localtime')=?", date);
        if (prRefund > 0.001) addMovementRow(layoutMovements, "↩ مرتجع مشتريات (نقدي)", fmt(prRefund) + " ر.ي (وارد)", true);

        // 4. إيرادات أخرى نقدية
        double otherCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM other_income WHERE date(income_date,'localtime')=? AND COALESCE(payment_method,'Cash')='Cash'", date);
        if (otherCash > 0.001) addMovementRow(layoutMovements, "💹 إيرادات أخرى (نقدي)", fmt(otherCash) + " ر.ي (وارد)", true);

        // 5. مصروفات نقدية
        double expCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE date(expense_date,'localtime')=? AND COALESCE(payment_method,'Cash')='Cash'", date);
        if (expCash > 0.001) addMovementRow(layoutMovements, "💸 مصروفات نقدية", fmt(expCash) + " ر.ي (صادر)", false);

        // 6. مشتريات نقدية
        double purchCash = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM purchases WHERE date(purchase_date,'localtime')=? AND COALESCE(payment_method,'كاش')='كاش'", date);
        if (purchCash > 0.001) addMovementRow(layoutMovements, "🚚 مشتريات نقدية", fmt(purchCash) + " ر.ي (صادر)", false);

        // 7. سداد موردين نقدي
        double spCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM supplier_payments WHERE date(payment_date,'localtime')=? AND COALESCE(payment_method,'كاش')='كاش'", date);
        if (spCash > 0.001) addMovementRow(layoutMovements, "🏭 سداد موردين (نقدي)", fmt(spCash) + " ر.ي (صادر)", false);

        // 8. مرتجعات مبيعات (نقدية للعميل)
        double retCash = safeScalar(db, "SELECT COALESCE(SUM(amount-debt_reduction),0) FROM returns WHERE date(return_date,'localtime')=?", date);
        if (retCash > 0.001) addMovementRow(layoutMovements, "↩ مرتجع مبيعات (نقدي)", fmt(retCash) + " ر.ي (صادر)", false);

        // 9. مبيعات شبكة
        double salesNet = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM sales WHERE date(sale_date,'localtime')=? AND payment_method='شبكة'", date);
        if (salesNet > 0.001) addMovementRow(layoutMovements, "🏧 مبيعات شبكة", fmt(salesNet) + " ر.ي (شبكة وارد)", true);

        if (layoutMovements.getChildCount() == 0) {
            addMovementRow(layoutMovements, "لا توجد حركات نقدية لهذا اليوم", "", true);
        }
    }

    private void addMovementRow(LinearLayout container, String label, String value, boolean isIn) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_cashbox_row, container, false);
        TextView tvLabel = row.findViewById(R.id.tvCashboxRowLabel);
        TextView tvValue = row.findViewById(R.id.tvCashboxRowValue);
        if (tvLabel != null) tvLabel.setText(label);
        if (tvValue != null) {
            tvValue.setText(value);
            tvValue.setTextColor(ContextCompat.getColor(this, isIn ? R.color.success : R.color.danger));
        }
        container.addView(row);
    }

    private double safeScalar(SQLiteDatabase db, String sql, String arg) {
        try {
            Cursor c = db.rawQuery(sql, new String[]{arg});
            try { return c.moveToFirst() ? c.getDouble(0) : 0.0; } finally { c.close(); }
        } catch (Exception e) { return 0.0; }
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
