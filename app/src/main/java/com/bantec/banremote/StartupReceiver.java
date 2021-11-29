package com.bantec.banremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartupReceiver extends BroadcastReceiver
{

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i("StartupReceiver : ", "スタートアップ起動開始");
    if(intent.getAction() == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
      Intent i = new Intent(context.getApplicationContext(), StartupService.class);
      context.startForegroundService(i);
    }
    if(intent.getAction() == Intent.ACTION_USER_PRESENT) {
      //Intent i = new Intent(context.getApplicationContext(), StartupService.class);
      //context.startForegroundService(i);
    }
  }
}