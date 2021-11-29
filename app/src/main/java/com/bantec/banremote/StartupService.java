package com.bantec.banremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.versionedparcelable.VersionedParcelize;

import java.util.List;
import java.util.Map;

public class StartupService extends Service {

  @Override
  public void onCreate() {
    Intent intent = new Intent(this, MainActivity.class);
    startActivity(intent);

    createNotificationChannel();
    Notification notification = new NotificationCompat.Builder(this, "primary_notification_channel")
        .setContentTitle("MyService is running")
        .setContentText("MyService is running")
        .build();
    startForeground(10, notification);
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  public void createNotificationChannel() {
    NotificationChannel notificationChannel = new NotificationChannel(
        "primary_notification_channel",
        "BanRemote",
        NotificationManager.IMPORTANCE_HIGH
    );
    notificationChannel.enableLights(true);
    notificationChannel.setLightColor(Color.RED);
    notificationChannel.enableVibration(true);
    notificationChannel.setDescription("BanRemoteが起動中です");

    NotificationManager notificationManager = (NotificationManager) this.getSystemService(
        this.NOTIFICATION_SERVICE);
    notificationManager.createNotificationChannel(
        notificationChannel);
  }
}
