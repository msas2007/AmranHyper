package com.lap1.mystore.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.User;
import com.lap1.mystore.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UsersActivity extends AppCompatActivity {

    private android.widget.LinearLayout layoutUsers;
    private DatabaseHelper dbHelper;
    private List<User> userList;

    private final String[] ROLES = {"admin", "manager", "cashier", "storekeeper"};
    private final String[] ROLE_LABELS = {"مدير عام", "مدير", "كاشير", "أمين مخزن"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        layoutUsers = findViewById(R.id.layoutUsersList);
        View btnBack = findViewById(R.id.btnUsersBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        Button btnAdd = findViewById(R.id.btnAddUser);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> showAddUserDialog());

        dbHelper = new DatabaseHelper(this);

        // Check permissions: only admin can manage users
        String role = SessionManager.getInstance(this).getRole();
        if (!"admin".equalsIgnoreCase(role) && !"manager".equalsIgnoreCase(role)) {
            Toast.makeText(this, "⛔ لا تملك صلاحية إدارة المستخدمين", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        loadUsers();
    }

    private void loadUsers() {
        userList = dbHelper.getAllUsers();
        if (layoutUsers == null) return;
        layoutUsers.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (User u : userList) {
            View row = inflater.inflate(R.layout.item_user_row, layoutUsers, false);
            TextView tvName = row.findViewById(R.id.tvUserFullName);
            TextView tvUsername = row.findViewById(R.id.tvUserUsername);
            TextView tvRole = row.findViewById(R.id.tvUserRole);
            TextView tvStatus = row.findViewById(R.id.tvUserStatus);
            ImageButton btnEdit = row.findViewById(R.id.btnEditUser);
            ImageButton btnToggle = row.findViewById(R.id.btnToggleUserActive);

            if (tvName != null) tvName.setText(u.getFullName());
            if (tvUsername != null) tvUsername.setText("@" + u.getUsername());
            if (tvRole != null) tvRole.setText(getRoleLabel(u.getRole()));
            if (tvStatus != null) {
                tvStatus.setText(u.isActive() ? "✅ نشط" : "🔴 معطل");
                tvStatus.setTextColor(getResources().getColor(u.isActive() ? R.color.success : R.color.danger, null));
            }
            if (btnEdit != null) {
                User finalU = u;
                btnEdit.setOnClickListener(v -> showEditUserDialog(finalU));
            }
            if (btnToggle != null) {
                User finalU = u;
                // Prevent deactivating own account
                boolean isSelf = u.getId() == SessionManager.getInstance(this).getUserId();
                btnToggle.setEnabled(!isSelf);
                btnToggle.setOnClickListener(v -> {
                    if (isSelf) { Toast.makeText(this, "لا يمكنك تعطيل حسابك الحالي", Toast.LENGTH_SHORT).show(); return; }
                    dbHelper.toggleUserActive(finalU.getId());
                    loadUsers();
                });
            }
            layoutUsers.addView(row);
        }
    }

    private void showAddUserDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, null);
        EditText etUsername = v.findViewById(R.id.etNewUsername);
        EditText etFullName = v.findViewById(R.id.etNewFullName);
        EditText etPassword = v.findViewById(R.id.etNewPassword);
        Spinner spRole = v.findViewById(R.id.spNewUserRole);

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ROLE_LABELS);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spRole != null) { spRole.setAdapter(roleAdapter); spRole.setSelection(2); } // default cashier

        new MaterialAlertDialogBuilder(this)
                .setTitle("➕ إضافة مستخدم جديد")
                .setView(v)
                .setPositiveButton("إضافة", (d, w) -> {
                    String username = etUsername != null ? etUsername.getText().toString().trim() : "";
                    String fullName = etFullName != null ? etFullName.getText().toString().trim() : "";
                    String password = etPassword != null ? etPassword.getText().toString() : "";
                    int roleIdx = spRole != null ? spRole.getSelectedItemPosition() : 2;
                    String role = ROLES[Math.min(roleIdx, ROLES.length - 1)];

                    if (username.isEmpty() || password.isEmpty()) {
                        Toast.makeText(this, "اسم المستخدم وكلمة المرور مطلوبان", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long id = dbHelper.addUser(username, password, fullName.isEmpty() ? username : fullName, role);
                    if (id > 0) { Toast.makeText(this, "✅ تم إضافة المستخدم", Toast.LENGTH_SHORT).show(); loadUsers(); }
                    else Toast.makeText(this, "❌ فشل — قد يكون اسم المستخدم مكرراً", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showEditUserDialog(User user) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, null);
        EditText etUsername = v.findViewById(R.id.etNewUsername);
        EditText etFullName = v.findViewById(R.id.etNewFullName);
        EditText etPassword = v.findViewById(R.id.etNewPassword);
        Spinner spRole = v.findViewById(R.id.spNewUserRole);

        if (etUsername != null) { etUsername.setText(user.getUsername()); etUsername.setEnabled(false); }
        if (etFullName != null) etFullName.setText(user.getFullName());
        if (etPassword != null) etPassword.setHint("اتركها فارغة لعدم التغيير");

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ROLE_LABELS);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spRole != null) {
            spRole.setAdapter(roleAdapter);
            int selIdx = getRoleIndex(user.getRole());
            spRole.setSelection(selIdx);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("✏️ تعديل المستخدم: " + user.getUsername())
                .setView(v)
                .setPositiveButton("حفظ", (d, w) -> {
                    String fullName = etFullName != null ? etFullName.getText().toString().trim() : user.getFullName();
                    String newPass = etPassword != null ? etPassword.getText().toString() : "";
                    int roleIdx = spRole != null ? spRole.getSelectedItemPosition() : 0;
                    String role = ROLES[Math.min(roleIdx, ROLES.length - 1)];

                    dbHelper.updateUser(user.getId(), fullName.isEmpty() ? user.getFullName() : fullName, role, user.isActive());
                    if (!newPass.isEmpty()) dbHelper.changeUserPassword(user.getId(), newPass);
                    Toast.makeText(this, "✅ تم تحديث بيانات المستخدم", Toast.LENGTH_SHORT).show();
                    loadUsers();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private String getRoleLabel(String role) {
        if (role == null) return "كاشير";
        switch (role.toLowerCase()) {
            case "admin": return "🔴 مدير عام";
            case "manager": return "🟠 مدير";
            case "cashier": return "🟢 كاشير";
            case "storekeeper": return "🔵 أمين مخزن";
            default: return role;
        }
    }

    private int getRoleIndex(String role) {
        if (role == null) return 2;
        for (int i = 0; i < ROLES.length; i++) {
            if (ROLES[i].equalsIgnoreCase(role)) return i;
        }
        return 2;
    }
}
