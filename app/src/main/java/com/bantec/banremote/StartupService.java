package com.bantec.banremote;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.versionedparcelable.VersionedParcelize;

import java.util.List;
import java.util.Map;

public class StartupService extends Service
{
  @Override
  public void onCreate() {
    Intent intent = new Intent(this, MainActivity.class);
    startActivity(intent);
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
