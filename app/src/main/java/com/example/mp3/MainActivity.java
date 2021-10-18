
package com.example.mp3;

import androidx.appcompat.app.AppCompatActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private TextView duration;
    private TextView songName;

    private Messenger messenger;
    private Messenger replyMessenger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Log.d("psylha", "MainActivity: onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        replyMessenger = new Messenger(new MyHandler());
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        duration = (TextView) findViewById(R.id.duration);
        songName = (TextView) findViewById(R.id.songName);

        final ListView lv = (ListView) findViewById(R.id.listView);
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.IS_MUSIC + "!= 0",
                null,
                null);
        lv.setAdapter(new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1,
                cursor,
                new String[] { MediaStore.Audio.Media.DATA},
                new int[] { android.R.id.text1 }));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> myAdapter,
                                    View myView,
                                    int myItemInt,
                                    long mylng) {
                Cursor c = (Cursor) lv.getItemAtPosition(myItemInt);
                String uri = c.getString(c.getColumnIndex(MediaStore.Audio.Media.DATA));
                startSong(uri);
            }
        });
        this.bindService(new Intent(this, MusicService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection serviceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Log.d("psylha", "MainActivity: onServiceConnected");
            messenger = new Messenger(service);
            Message message = Message.obtain(null, MusicService.REGISTER, 0, 0);
            message.replyTo = replyMessenger;
            try {
                messenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            //Log.d("psylha", "MainActivity: onServiceDisconnected");
            messenger = null;
            Message message = Message.obtain(null, MusicService.UNREGISTER, 0, 0);
            try {
                messenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    /*
     *  Function to load song in media player. Sends song's filepath to MusicService
     */
    private void startSong(String uri) {
        //Log.d("psylha", "MainActivity: start song -> " + uri);
        songName.setText(uri.substring(uri.lastIndexOf('/')+1));

        Message startMessage = Message.obtain(null, MusicService.START, 0, 0);
        Bundle b = new Bundle(); b.putString("uri", uri);
        startMessage.setData(b);
        try {
            messenger.send(startMessage);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /*
     *  Handles all media button clicks (play, pause, stop).
     *  Communicates to MusicService of intended action.
     */
    public void mediaClick(View v) {
        switch (v.getId()) {
            case R.id.playButton:
                Message playMessage = Message.obtain(null, MusicService.PLAY, 0, 0);
                try {
                    messenger.send(playMessage);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.pauseButton:
                Message pauseMessage = Message.obtain(null, MusicService.PAUSE, 0, 0);
                try {
                    messenger.send(pauseMessage);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.stopButton:
                progressBar.setProgress(0);
                songName.setText(getString(R.string.select_song));
                duration.setText(getString(R.string.empty_song));
                Message stopMessage = Message.obtain(null, MusicService.STOP, 0, 0);
                try {
                    messenger.send(stopMessage);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //Log.d("psylha", "MainActivity: onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        //Log.d("psylha", "MainActivity: onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        //Log.d("psylha", "MainActivity: onStop");
        super.onStop();
    }

    @Override
    protected void onResume() {
        //Log.d("psylha", "MainActivity: onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        //Log.d("psylha", "MainActivity: onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        //Log.d("psylha", "MainActivity: onDestroy");
        try {
            messenger.send(Message.obtain(null, MusicService.UNREGISTER));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /*
     *  Handles for all messages from MusicService
     */
    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MusicService.START: // To ensure song name is properly shown
                    songName.setText(msg.getData().getString("name"));
                    break;
                case MusicService.PROGRESS: // Updates all necessary UI components
                    progressBar.setMax(msg.arg2);
                    progressBar.setProgress(msg.arg1);
                    int progressSec = msg.arg1/1000;
                    int maxSec = msg.arg2/1000;
                    duration.setText(String.format(Locale.ROOT,"%02d:%02d / %02d:%02d",
                            (progressSec/60),(progressSec%60),(maxSec/60),(maxSec%60)));
                    break;
                case MusicService.STOP: // Ensures UI components are reset
                    //Log.d("psylha", "MainActivity: STOP Received");
                    songName.setText(getString(R.string.select_song));
                    progressBar.setProgress(0);
                    duration.setText(getString(R.string.empty_song));
                    break;
                default:
                    break;
            }
        }
    }
}
