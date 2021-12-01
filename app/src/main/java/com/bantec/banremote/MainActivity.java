package com.bantec.banremote;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;
import io.skyway.Peer.Room;
import io.skyway.Peer.RoomDataMessage;
import io.skyway.Peer.RoomOption;

public class MainActivity extends Activity {
  private static final String TAG = MainActivity.class.getSimpleName();

  //
  // Set your APIkey and Domain
  //
  private static final String API_KEY = "35dd9a9d-8bb8-4d82-9d4c-f45e8a8b5bc9";
  private static final String DOMAIN = "ban-remote.com";

  private Peer _peer;
  private MediaStream _localStream;
  private Room _room;
  private RemoteViewAdapter _adapter;

  private String _strOwnId;
  private boolean _bConnected;

  private Handler _handler;

  private MediaConstraints _constraints;

  private RoomOption _option;

  private ProgressDialog progressDialog = null;

  private NetworkManager networkManager = null;



  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    //Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    //startActivityForResult(intent, 2);

    networkManager = new NetworkManager(this, getIntent(), this, this::onNetworkConnected);
    if(!networkManager.isNetworkConnected()) {
      networkManager.connectToNetwork();
    }
    else {
      initApp();
    }
  }

  public void onNetworkConnected() {
    //initApp();
  }

  @Override
  public void onBackPressed() {
    // ダイアログ表示
    new AlertDialog.Builder(this)
        .setTitle("遠隔支援アプリを終了しますか？")
        .setMessage("遠隔アプリを終了して良いですか？再度アプリを起動するまで、支援を受けることはできません。")
        .setPositiveButton("OK", (dialog, which) -> {
          // OKが押された場合、Activity を終了し、前のページへ
          android.os.Process.killProcess(android.os.Process.myPid());
        })
        .setNegativeButton("キャンセル", null)
        .show();
  }

  private void initApp() {
    initSkyway();
  }



  private void initSkyway() {
    _handler = new Handler(Looper.getMainLooper());
    final Activity activity = this;

    //
    // Initialize Peer
    //
    PeerOption option = new PeerOption();
    option.key = API_KEY;
    option.domain = DOMAIN;
    _peer = new Peer(this, "GoogleGlass", option);

    //
    // Set Peer event callbacks
    //

    // OPEN
    _peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
      @Override
      public void onCallback(Object object) {
        // Show my ID
        _strOwnId = (String) object;

        // Request permissions
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
        } else {
          // Get a local MediaStream & show it
          startLocalStream();
        }
        joinRoom();
      }
    });

    _peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback() {
      @Override
      public void onCallback(Object object) {
        Log.d(TAG, "[On/Close]");
      }
    });
    _peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
      @Override
      public void onCallback(Object object) {
        PeerError error = (PeerError) object;
        Log.d(TAG, "[On/Error]" + error.getMessage());
      }
    });


    //
    // Set GUI event listeners
    //

    Button btnAction = (Button) findViewById(R.id.btnAction);
    btnAction.setEnabled(true);
    btnAction.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        v.setEnabled(false);

        if (!_bConnected) {
          // Join room
          joinRoom();
        } else {
          // Leave room
          leaveRoom();
        }
        v.setEnabled(true);
      }
    });


    //
    // Set GridView for Remote Video Stream
    //
    GridView grdRemote = (GridView) findViewById(R.id.grdRemote);
    if (null != grdRemote) {
      _adapter = new RemoteViewAdapter(this);
      grdRemote.setAdapter(_adapter);
    }
  }


  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    switch (requestCode) {
      case 0: {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          startLocalStream();
        }
        else {
          Toast.makeText(this,"Failed to access the camera and microphone.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show();
        }
        break;
      }
      case 1: {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        }
        else {
          Toast.makeText(this,"Failed to access the wifi scan.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show();
        }
        break;
      }
    }
  }

  @Override
  protected void onStart() {
    super.onStart();

    // Hide the status bar.
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

    // Disable Sleep and Screen Lock
    Window wnd = getWindow();
    wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Set volume control stream type to WebRTC audio.
    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
  }

  @Override
  protected void onPause() {
    // Set default volume control stream type.
    setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
    super.onPause();
  }

  @Override
  protected void onStop()	{
    // Enable Sleep and Screen Lock
    Window wnd = getWindow();
    wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    destroyPeer();
    super.onDestroy();
  }

  //
  // Get a local MediaStream & show it
  //
  void startLocalStream() {
    Navigator.initialize(_peer);
    _constraints = new MediaConstraints();
    _constraints.maxWidth = 1920;
    _constraints.maxHeight = 1080;
    _localStream = Navigator.getUserMedia(_constraints);
    Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
    _localStream.setEnableAudioTrack(0, true);
    _localStream.addVideoRenderer(canvas,0);
  }

  //
  // Clean up objects
  //
  private void destroyPeer() {
    leaveRoom();

    if (null != _localStream) {
      Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
      _localStream.removeVideoRenderer(canvas,0);
      _localStream.close();
    }

    Navigator.terminate();

    if (null != _peer) {
      unsetPeerCallback(_peer);

      if (!_peer.isDestroyed()) {
        _peer.destroy();
      }

      _peer = null;
    }
  }

  //
  // Unset callbacks for PeerEvents
  //
  void unsetPeerCallback(Peer peer) {
    if(null == _peer){
      return;
    }

    peer.on(Peer.PeerEventEnum.OPEN, null);
    peer.on(Peer.PeerEventEnum.CONNECTION, null);
    peer.on(Peer.PeerEventEnum.CALL, null);
    peer.on(Peer.PeerEventEnum.CLOSE, null);
    peer.on(Peer.PeerEventEnum.ERROR, null);
  }

  //
  // Join the room
  //
  void joinRoom() {
    if ((null == _peer) || (null == _strOwnId) || (0 == _strOwnId.length())) {
      Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show();
      return;
    }

    // Get room name
    EditText edtRoomName = (EditText)findViewById(R.id.txRoomName);
    String roomName = edtRoomName.getText().toString();
    if (TextUtils.isEmpty(roomName)) {
      Toast.makeText(this, "You should input room name.", Toast.LENGTH_SHORT).show();
      return;
    }

    _option = new RoomOption();
    _option.mode = RoomOption.RoomModeEnum.SFU;
    _option.stream = _localStream;
    // Join Room
    _room = _peer.joinRoom(roomName, _option);
    _bConnected = true;

    //
    // Set Callbacks
    //
    _room.on(Room.RoomEventEnum.OPEN, new OnCallback() {
      @Override
      public void onCallback(Object object) {
        if (!(object instanceof String)) return;

        String roomName = (String)object;
        Log.i(TAG, "Enter Room: " + roomName);
        Toast.makeText(MainActivity.this, "Enter Room: " + roomName, Toast.LENGTH_SHORT).show();
      }
    });

    _room.on(Room.RoomEventEnum.CLOSE, new OnCallback() {
      @Override
      public void onCallback(Object object) {
        String roomName = (String)object;

        networkManager.isOkNetwork("再接続しています", "通信が遮断されました。再接続しています。");

        // Remove all streams
        _adapter.removeAllRenderers();

        // Unset callbacks
        _room.on(Room.RoomEventEnum.OPEN, null);
        _room.on(Room.RoomEventEnum.CLOSE, null);
        _room.on(Room.RoomEventEnum.ERROR, null);
        _room.on(Room.RoomEventEnum.PEER_JOIN, null);
        _room.on(Room.RoomEventEnum.PEER_LEAVE, null);
        _room.on(Room.RoomEventEnum.STREAM, null);

        _room = null;
        _bConnected = false;
        updateActionButtonTitle();
      }
    });

    _room.on(Room.RoomEventEnum.ERROR, new OnCallback()
    {
      @Override
      public void onCallback(Object object)
      {
        PeerError error = (PeerError) object;
        Log.d(TAG, "RoomEventEnum.ERROR:" + error);
      }
    });

    _room.on(Room.RoomEventEnum.PEER_JOIN, new OnCallback()
    {
      @Override
      public void onCallback(Object object)
      {
        Log.d(TAG, "RoomEventEnum.PEER_JOIN:");

        if (!(object instanceof String)) return;

        String peerId = (String)object;
        Log.i(TAG, "Join Room: " + peerId);
        Toast.makeText(MainActivity.this, peerId + " has joined.", Toast.LENGTH_LONG).show();
      }
    });
    _room.on(Room.RoomEventEnum.PEER_LEAVE, new OnCallback()
    {
      @Override
      public void onCallback(Object object)
      {
        Log.d(TAG, "RoomEventEnum.PEER_LEAVE:");

        if (!(object instanceof String)) return;

        String peerId = (String)object;
        Log.i(TAG, "Leave Room: " + peerId);
        Toast.makeText(MainActivity.this, peerId + " has left.", Toast.LENGTH_LONG).show();

        _adapter.remove(peerId);
      }
    });

    _room.on(Room.RoomEventEnum.STREAM, new OnCallback()
    {
      @Override
      public void onCallback(Object object)
      {
        Log.d(TAG, "RoomEventEnum.STREAM: + " + object);

        if (!(object instanceof MediaStream)) return;

        final MediaStream stream = (MediaStream)object;
        Log.d(TAG, "peer = " + stream.getPeerId() + ", label = " + stream.getLabel());

        _adapter.add(stream);
      }
    });

    _room.on(Room.RoomEventEnum.DATA, new OnCallback() {
      @Override
      public void onCallback(Object object) {
        Log.d(TAG, "RoomEventEnum.DATA:");

        if (!(object instanceof RoomDataMessage)) return;

        RoomDataMessage msg = (RoomDataMessage)object;

        String mes = (String)msg.data;
        Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
        //Toast.makeText(MainActivity.this, mes, Toast.LENGTH_LONG).show();

        switch (mes) {
          case "cmd#%CameraOn#%#":
            _localStream.setEnableVideoTrack(0, true);
            break;
          case "cmd#%CameraOff#%#":
            _localStream.setEnableVideoTrack(0, false);
            break;
          default:
            break;
        }
        if(mes.startsWith("cmd#%OffsetX=")) {
          /*
          int sy = mes.indexOf("OffsetY");
          String x = mes.substring(13, sy); //ここから
          int end = mes.indexOf("#%#");
          String y = mes.substring(sy+7, end);
          Toast.makeText(MainActivity.this, "x: " + x, Toast.LENGTH_LONG).show();
          Toast.makeText(MainActivity.this, "y: " + y, Toast.LENGTH_LONG).show();


          int ix = Integer.parseInt(x);
          int iy = Integer.parseInt(y);
          Paint paint = new Paint();
          paint.setColor(Color.RED);
          paint.setStrokeWidth(5);
          paint.setAntiAlias(true);
          paint.setStyle(Paint.Style.STROKE);
          android.graphics.Canvas can = new android.graphics.Canvas();
          can.drawCircle(ix, iy, 10, paint);
          canvas.draw(new android.graphics.Canvas());
          Toast.makeText(MainActivity.this, mes, Toast.LENGTH_LONG).show();*/
        } else if (mes.startsWith("cmd#%InitX=")) {
          int sy = mes.indexOf("InitY");
          String x = mes.substring(11, sy);
          int end = mes.indexOf("#%#");
          String y = mes.substring(sy+8, end);
          int ix = Integer.parseInt(x);
          int iy = Integer.parseInt(y);
          Toast.makeText(MainActivity.this, mes, Toast.LENGTH_LONG).show();
        }
      }
    });

    // Update UI
    updateActionButtonTitle();
  }

  //
  // Leave the room
  //
  void leaveRoom() {
    if (null == _peer || null == _room) {
      return;
    }
    _room.close();
  }

  //
  // Update actionButton title
  //
  void updateActionButtonTitle() {
    _handler.post(new Runnable() {
      @Override
      public void run() {
        Button btnAction = (Button) findViewById(R.id.btnAction);
        if (null != btnAction) {
          if (!_bConnected) {
            btnAction.setText("Join Room");
          } else {
            btnAction.setText("Leave Room");
          }
        }
      }
    });
  }
}
