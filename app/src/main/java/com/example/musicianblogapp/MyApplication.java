package com.example.musicianblogapp;

import android.app.Application;
import com.example.musicianblogapp.NotificationHelper; // Importáld a Helpert

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Értesítési csatornák létrehozása az alkalmazás indításakor
        NotificationHelper.createNotificationChannels(this);
    }
}