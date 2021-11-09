package com.bantec.banremote;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.MediaConnection;
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

  private Peer			_peer;
  private MediaStream		_localStream;
  private Room _room;
  private RemoteViewAdapter   _adapter;

  private String			_strOwnId;
  private boolean			_bConnected;

  private Handler _handler;

  private MediaConstraints _constraints;

  private RoomOption _option;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    if(!isNetworkConnected()) {
      Toast.makeText(this, "ネットワークに接続されていません。", Toast.LENGTH_SHORT).show();
      connectToNetwork("GoogleGlass", "mumblepasswod1234", "WPA");
      return;
    }


    _handler = new Handler(Looper.getMainLooper());
    final Activity activity = this;

    //
    // Initialize Peer
    //
    PeerOption option = new PeerOption();
    option.key = API_KEY;
    option.domain = DOMAIN;
    _peer = new Peer(this, option);

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
          ActivityCompat.requestPermissions(activity,new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},0);
        }
        else {
          // Get a local MediaStream & show it
          startLocalStream();
        }
        joinRoom();
      }
    });

    _peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback()	{
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
    btnAction.setOnClickListener(new View.OnClickListener()	{
      @Override
      public void onClick(View v)	{
        v.setEnabled(false);

        if (!_bConnected) {
          // Join room
          joinRoom();
        }
        else {
          // Leave room
          leaveRoom();
        }

        v.setEnabled(true);
      }
    });


    //
    // Set GridView for Remote Video Stream
    //
    GridView grdRemote = (GridView)findViewById(R.id.grdRemote);
    if (null != grdRemote) {
      _adapter = new RemoteViewAdapter(this);
      grdRemote.setAdapter(_adapter);
    }
  }

  private boolean isNetworkConnected() {
    //
    // Check network activity
    //
    ConnectivityManager cm =
        (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);

    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    return activeNetwork != null &&
        activeNetwork.isConnectedOrConnecting();
  }

  private void connectToNetwork(String networkSSID, String networkPass, String networkCapabilities) {
    WifiConfiguration wifiConfig= new WifiConfiguration();
    wifiConfig.SSID=networkSSID;
    if (networkCapabilities.toUpperCase().contains("WEP")) { //WEP Network.
      Toast.makeText(this, "WEP Network", Toast.LENGTH_SHORT).show();
      wifiConfig.wepKeys[0]= networkPass;
      wifiConfig.wepTxKeyIndex= 0;
      wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
      wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
    } else if (networkCapabilities.toUpperCase().contains("WPA")) { //WPA Network
      Toast.makeText(this, "WPA Network", Toast.LENGTH_SHORT).show();
      wifiConfig.preSharedKey= networkPass;
    } else { //OPEN Network.
      Toast.makeText(this, "OPEN Network", Toast.LENGTH_SHORT).show();
      wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
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
        Log.i(TAG, "Leave Room: " + roomName);
        Toast.makeText(MainActivity.this, "Leave Room: " + roomName, Toast.LENGTH_LONG).show();

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
          int sy = mes.indexOf("OffsetY");
          String x = mes.substring(13, sy); //ここから
          int end = mes.indexOf("#%#");
          String y = mes.substring(sy+7, end);
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
          Toast.makeText(MainActivity.this, mes, Toast.LENGTH_LONG).show();
        } else if (mes.startsWith("cmd#%InitX=")) {
          int sy = mes.indexOf("InitY");
          String x = mes.substring(11, sy);
          int end = mes.indexOf("#%#");
          String y = mes.substring(sy+8, end);
          int ix = Integer.parseInt(x);
          int iy = Integer.parseInt(y);
          Toast.makeText(MainActivity.this, "Init", Toast.LENGTH_LONG).show();
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
