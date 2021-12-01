package com.bantec.banremote;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.function.Function;

public class NetworkManager {
  Context context = null;
  Intent intent = null;
  Activity activity = null;

  ProgressDialog progressDialog = null;
  WifiManager wifiManager = null;
  BroadcastReceiver wifiScanReceiver = null;

  public NetworkManager(Context _context, Intent _intent, Activity _activity, Runnable _onNetworkConnected) {
    context = _context;
    intent = _intent;
    activity = _activity;

    progressDialog = new ProgressDialog(context);
  }

  public void connectToNetwork() {
    isOkNetwork("ネットワークに接続しています", "このままお待ちください。");
  }


  public void isOkNetwork(String title, String message) {
    if (!isNetworkConnected()) {
      progressDialog = new ProgressDialog(context);
      progressDialog.setTitle(title);
      progressDialog.setMessage(message);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      progressDialog.show();
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
      if (wifiManager.isWifiEnabled() == false) {
        wifiManager.setWifiEnabled(true);
      }

      wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
          if (success) {
            scanSuccess();
          } else {
            scanFailure();
          }
        }
      };

      if (ContextCompat.checkSelfPermission(context,
          Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CHANGE_WIFI_STATE}, 0);
      } else {
      }

      if (ContextCompat.checkSelfPermission(context,
          Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
      } else {
        activity.registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        boolean success = wifiManager.startScan();
        if (!success) {
          scanFailure();
        }
      }
    }
  }

  private void scanSuccess() {
    int id = -1;
    for(int i = 0; i < wifiManager.getScanResults().size(); i++) {
      ScanResult sr = wifiManager.getScanResults().get(i);
      Log.i("main", "SSID" + i + ": " + sr.SSID);
      if(sr.SSID.contains("GoogleGlass"))
        id = i;
    }

    if(id < 0) {
      progressDialog.setTitle("【BR-001】操作が必要です");
      progressDialog.setMessage("テザリングを有効にしてください。その後自動で接続されます。このままお待ちください。");
      //Toast.makeText(this, "Google Glass用のWi-Fi APが見つかりませんでした。", Toast.LENGTH_SHORT).show();
      return;
    }

    progressDialog.setTitle("ネットワークに接続しています");
    progressDialog.setMessage("このままお待ちください。");
    ScanResult connectScanResult = wifiManager.getScanResults().get(id);

    @Nullable
    List<WifiConfiguration> configuredNetworks;

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      Log.i("", "位置情報の権限がありません。");
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
      //GUIdで権限を設定する
      return;
    }
    configuredNetworks = wifiManager.getConfiguredNetworks();
    try {
      Log.i("main", "configuredNetworks = OK");
      configuredNetworks = wifiManager.getConfiguredNetworks();
    }
    catch (Exception e) {
      Log.i("main", "configuredNetworks = null");
      configuredNetworks = null;
    }

    int networkId = -1;
    if(configuredNetworks != null) {
      for(int i = 0; i < configuredNetworks.size(); i++) {
        WifiConfiguration configuredNetwork = configuredNetworks.get(i);
        // "（ダブルクォーテーション） が前後についているので除外している
        String normalizedSsid = configuredNetwork.SSID.substring(1,
            configuredNetwork.SSID.length() - 1);

        if(connectScanResult.SSID.equals(normalizedSsid)) {
          networkId = configuredNetwork.networkId;
          break;
        }
      }
    }
    if(networkId < 0) {
      //接続情報がないので作る
      WifiConfiguration configuration = new WifiConfiguration();
      Log.e("main", connectScanResult.SSID);
      configuration.SSID = "\"" + connectScanResult.SSID + "\"";
      configuration.allowedProtocols.clear();
      configuration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
      configuration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
      configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
      configuration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
      configuration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
      configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
      configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
      configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
      configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
      configuration.preSharedKey = "\"mumblepasswod1234\"";

      networkId = wifiManager.addNetwork(configuration);
      if(networkId == -1)
        Log.e("main", "Configuration failed.");

    }
    if(networkId < 0) {
      Toast.makeText(context, "接続できませんでした", Toast.LENGTH_SHORT).show();
      return;
    }

    if (wifiManager.enableNetwork(networkId, true)) {
      progressDialog.dismiss();
      unregisterNetworkChanges();
      Intent intent = new Intent(context, MainActivity.class);
      activity.startActivity(intent);
    } else {
      Toast.makeText(context, "接続できませんでした", Toast.LENGTH_SHORT).show();
    }
  }
  private void scanFailure() {

  }

  private void unregisterNetworkChanges() {
    activity.unregisterReceiver(wifiScanReceiver);
  }

  public boolean isNetworkConnected() {
    //
    // Check network activity
    //
    ConnectivityManager cm =
        (ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);

    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    return activeNetwork != null &&
        activeNetwork.isConnectedOrConnecting();
  }
}
