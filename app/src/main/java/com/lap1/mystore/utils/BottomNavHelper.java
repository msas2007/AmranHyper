package com.lap1.mystore.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.lap1.mystore.R;
import com.lap1.mystore.activities.BackupActivity;
import com.lap1.mystore.activities.CategoriesActivity;
import com.lap1.mystore.activities.ClosingActivity;
import com.lap1.mystore.activities.CustomersActivity;
import com.lap1.mystore.activities.DashboardActivity;
import com.lap1.mystore.activities.ExpensesActivity;
import com.lap1.mystore.activities.ExpiryActivity;
import com.lap1.mystore.activities.InvoicesActivity;
import com.lap1.mystore.activities.ProductsActivity;
import com.lap1.mystore.activities.ReportsActivity;
import com.lap1.mystore.activities.SalesActivity;

public class BottomNavHelper {

    public static void setupBottomNav(final Activity activity, String activeTab) {
        View bottomNavHome = activity.findViewById(R.id.bottomNavHome);
        View bottomNavPos = activity.findViewById(R.id.bottomNavPos);
        View bottomNavInvoices = activity.findViewById(R.id.bottomNavInvoices);
        View bottomNavProducts = activity.findViewById(R.id.bottomNavProducts);
        View bottomNavMore = activity.findViewById(R.id.bottomNavMore);

        if (bottomNavHome == null) return;

        TextView tvHomeText = activity.findViewById(R.id.tvBottomNavHomeText);
        TextView tvPosText = activity.findViewById(R.id.tvBottomNavPosText);
        TextView tvInvoicesText = activity.findViewById(R.id.tvBottomNavInvoicesText);
        TextView tvProductsText = activity.findViewById(R.id.tvBottomNavProductsText);
        TextView tvMoreText = activity.findViewById(R.id.tvBottomNavMoreText);

        ImageView ivHomeIcon = activity.findViewById(R.id.ivBottomNavHomeIcon);
        ImageView ivPosIcon = activity.findViewById(R.id.ivBottomNavPosIcon);
        ImageView ivInvoicesIcon = activity.findViewById(R.id.ivBottomNavInvoicesIcon);
        ImageView ivProductsIcon = activity.findViewById(R.id.ivBottomNavProductsIcon);
        ImageView ivMoreIcon = activity.findViewById(R.id.ivBottomNavMoreIcon);

        int primaryColor = ContextCompat.getColor(activity, R.color.primary);

        // Highlight active tab
        if ("home".equalsIgnoreCase(activeTab)) {
            if (tvHomeText != null) {
                tvHomeText.setTextColor(primaryColor);
                tvHomeText.setTypeface(null, Typeface.BOLD);
            }
            if (ivHomeIcon != null) ivHomeIcon.setColorFilter(primaryColor);
        } else if ("pos".equalsIgnoreCase(activeTab)) {
            if (tvPosText != null) {
                tvPosText.setTextColor(primaryColor);
                tvPosText.setTypeface(null, Typeface.BOLD);
            }
            if (ivPosIcon != null) ivPosIcon.setColorFilter(primaryColor);
        } else if ("invoices".equalsIgnoreCase(activeTab)) {
            if (tvInvoicesText != null) {
                tvInvoicesText.setTextColor(primaryColor);
                tvInvoicesText.setTypeface(null, Typeface.BOLD);
            }
            if (ivInvoicesIcon != null) ivInvoicesIcon.setColorFilter(primaryColor);
        } else if ("products".equalsIgnoreCase(activeTab) || "inventory".equalsIgnoreCase(activeTab)) {
            if (tvProductsText != null) {
                tvProductsText.setTextColor(primaryColor);
                tvProductsText.setTypeface(null, Typeface.BOLD);
            }
            if (ivProductsIcon != null) ivProductsIcon.setColorFilter(primaryColor);
        } else if ("more".equalsIgnoreCase(activeTab)) {
            if (tvMoreText != null) {
                tvMoreText.setTextColor(primaryColor);
                tvMoreText.setTypeface(null, Typeface.BOLD);
            }
            if (ivMoreIcon != null) ivMoreIcon.setColorFilter(primaryColor);
        }

        bottomNavHome.setOnClickListener(v -> {
            if (activity instanceof DashboardActivity) {
                View scroll = activity.findViewById(R.id.scrollViewDashboard);
                if (scroll != null) scroll.scrollTo(0, 0);
            } else {
                Intent intent = new Intent(activity, DashboardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
            }
        });

        bottomNavPos.setOnClickListener(v -> {
            if (!(activity instanceof SalesActivity)) {
                Intent intent = new Intent(activity, SalesActivity.class);
                activity.startActivity(intent);
            }
        });

        bottomNavInvoices.setOnClickListener(v -> {
            if (!(activity instanceof InvoicesActivity)) {
                Intent intent = new Intent(activity, InvoicesActivity.class);
                activity.startActivity(intent);
            }
        });

        if (bottomNavProducts != null) {
            bottomNavProducts.setOnClickListener(v -> {
                if (!(activity instanceof ProductsActivity)) {
                    Intent intent = new Intent(activity, ProductsActivity.class);
                    activity.startActivity(intent);
                }
            });
        }

        bottomNavMore.setOnClickListener(v -> showMoreServicesDialog(activity));
    }

    public static void showMoreServicesDialog(final Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_more_services);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        Button btnReports = dialog.findViewById(R.id.dialogMoreBtnReports);
        Button btnExpenses = dialog.findViewById(R.id.dialogMoreBtnExpenses);
        Button btnCategories = dialog.findViewById(R.id.dialogMoreBtnCategories);
        Button btnBackup = dialog.findViewById(R.id.dialogMoreBtnBackup);
        Button btnExpiry = dialog.findViewById(R.id.dialogMoreBtnExpiry);
        Button btnClosing = dialog.findViewById(R.id.dialogMoreBtnClosing);
        Button btnPermissions = dialog.findViewById(R.id.dialogMoreBtnPermissions);
        Button btnDebts = dialog.findViewById(R.id.dialogMoreBtnDebts);
        Button btnClose = dialog.findViewById(R.id.dialogMoreBtnClose);

        if (btnReports != null) {
            btnReports.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, ReportsActivity.class));
            });
        }

        if (btnExpenses != null) {
            btnExpenses.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, ExpensesActivity.class));
            });
        }

        if (btnCategories != null) {
            btnCategories.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, CategoriesActivity.class));
            });
        }

        if (btnBackup != null) {
            btnBackup.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, BackupActivity.class));
            });
        }

        if (btnPermissions != null) {
            btnPermissions.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(activity, BackupActivity.class);
                intent.putExtra("FOCUS_CREDENTIALS", true);
                activity.startActivity(intent);
            });
        }

        if (btnExpiry != null) {
            btnExpiry.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, ExpiryActivity.class));
            });
        }

        if (btnClosing != null) {
            btnClosing.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, ClosingActivity.class));
            });
        }

        if (btnDebts != null) {
            btnDebts.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, CustomersActivity.class));
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
