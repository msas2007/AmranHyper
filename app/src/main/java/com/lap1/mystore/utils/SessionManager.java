package com.lap1.mystore.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.lap1.mystore.models.User;

public class SessionManager {

    private static final String PREF_NAME = "MyStoreUserSession";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_ROLE = "role";

    private static SessionManager instance;
    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    private SessionManager(Context context) {
        pref = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void createLoginSession(int userId, String username, String fullName, String role) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_FULL_NAME, (fullName != null && !fullName.trim().isEmpty()) ? fullName : username);
        editor.putString(KEY_ROLE, (role != null && !role.trim().isEmpty()) ? role : "admin");
        editor.apply();
    }

    public void createLoginSession(User user) {
        if (user != null) {
            createLoginSession(user.getId(), user.getUsername(), user.getFullName(), user.getRole());
        }
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return pref.getInt(KEY_USER_ID, -1);
    }

    public String getUsername() {
        return pref.getString(KEY_USERNAME, "");
    }

    public String getFullName() {
        String name = pref.getString(KEY_FULL_NAME, "");
        return (!name.isEmpty()) ? name : getUsername();
    }

    public String getRole() {
        return pref.getString(KEY_ROLE, "admin");
    }

    public void logoutUser() {
        editor.clear();
        editor.apply();
    }
}
