package com.example.mp3;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import java.util.ArrayList;
import androidx.core.app.NotificationCompat;

public class MusicService extends Service {
    private Messenger messenger;
    private MP3Player mp3;
    private ProgressUpdater progressUpdater;

    public static final int REGISTER = 0;
    public static final int UNREGISTER = 1;
    public static final int START = 2;
    public static final int PLAY = 3;
    public static final int PAUSE = 4;
    public static final int STOP = 5;
    public static final int PROGRESS = 6;

    ArrayList<Messenger> clientMessengers = new ArrayList<Messenger>();

    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;
    private int previousPosition;

    @Override
    public void onCreate() {
        //Log.d("psylha", "* MusicService: onCreate");
        super.onCreate();
        messenger = new Messenger(new MyHandler());
        mp3 = new MP3Player();
        progressUpdater = null;
        initNotification();
    }

    @Override
    public void onDestroy() {
        //Log.d("psylha", "* MusicService: onDestroy");
        stopMusic();
        notificationManager.cancelAll();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        //Log.d("psylha", "* MusicService: onBind");
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //Log.d("psylha", "* MusicService: onUnbind");
        return super.onUnbind(intent);
    }

    protected void loadMusic(String uri) {
        // Handles case for reloading the same song
        if(uri.equalsIgnoreCase(mp3.getFilePath()) &&
                mp3.getState() != MP3Player.MP3PlayerState.STOPPED)
            return;

        stopMusic(); // Ensure no other music is being played currently

        // Handle race cases, when user switches too quickly between songs.
        // For example, song is stopped as soon as it is started.
        try {
            Thread.sleep(150);
        } catch(Exception e) {
            e.printStackTrace(); return;
        }
        mp3.load(uri);
        previousPosition = -1;
        progressUpdater = new ProgressUpdater(); // Start thread to update components

        // Send currently loaded song's name to all clients
        Message message = Message.obtain(null, MusicService.START);
        Bundle b = new Bundle();
        b.putString("name",
                mp3.getFilePath().substring(mp3.getFilePath().lastIndexOf('/')+1));
        message.setData(b);
        for(int i = 0; i < clientMessengers.size(); i++) {
            try {
                clientMessengers.get(i).send(message);
            } catch (RemoteException e) {
                clientMessengers.remove(i);
            }
        }

        // Create notification
        builder.setContentTitle("MP3 Player")
                .setContentText(uri.substring(uri.lastIndexOf('/')+1))
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(mp3.getDuration(),0,false)
                .setVibrate(null)
                .setSound(null);
        notificationManager.notify(1, builder.build());
    }

    protected void playMusic() {
        if(mp3.getState() == MP3Player.MP3PlayerState.PAUSED) {
            //Log.d("psylha", "* MusicService: playMusic");
            mp3.play();
        }
    }

    protected void pauseMusic() {
        if(mp3.getState() == MP3Player.MP3PlayerState.PLAYING) {
            //Log.d("psylha", "* MusicService: pauseMusic");
            mp3.pause();
        }
    }

    protected void stopMusic() {
        if(mp3.getState() != MP3Player.MP3PlayerState.STOPPED) {
            //Log.d("psylha", "* MusicService: stopMusic (" + mp3.getFilePath() + ")");
            mp3.stop();
            progressUpdater = null;
        }
    }

    /*
     *  Update notification
     */
    private void progressNotification() {
        builder.setProgress(mp3.getDuration(), mp3.getProgress(),false);
        notificationManager.notify(1, builder.build());
    }

    /*
     *  Handles all necessary progress update processes.
     *  Checks if state should be updated to STOPPED.
     */
    private int progress() {
        progressNotification();
        int progress = mp3.getProgress();
        //Log.d("psylha","poscheck: " + previousPosition + " -> " + progress);

        // Previous position and current position of song compared.
        // There are cases where song ends and the current position stays less than its duration
        if((progress == previousPosition && mp3.getState() == MP3Player.MP3PlayerState.PLAYING) ||
                progress >= mp3.getDuration()) {
            stopMusic();
            return 0;
        }
        previousPosition = progress;
        return previousPosition;
    }

    private void initNotification() {
        notificationManager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "MusicService";
            String description = "Music";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("100", name,
                    importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, intent, 0);
        builder = new NotificationCompat.Builder(this, "100");
        builder.setContentIntent(pendingIntent);
    }

    protected class ProgressUpdater extends Thread implements Runnable {
        public ProgressUpdater() {
            this.start();
        }

        public void run() {
            while(mp3.getProgress() < mp3.getDuration()) {
                try {
                    Thread.sleep(100);
                } catch(Exception e) {
                    e.printStackTrace(); return;
                }

                for(int i = 0; i < clientMessengers.size(); i++) {
                    try {
                        // Sends current position and total duration to clients
                        Message message = Message.obtain(null, MusicService.PROGRESS,
                                        MusicService.this.progress(), mp3.getDuration());
                        clientMessengers.get(i).send(message);
                    } catch (RemoteException e) {
                        clientMessengers.remove(i);
                    }
                }
            }
            notificationManager.cancel(1); // Remove notification once song ends
            for(int i = 0; i < clientMessengers.size(); i++) {
                try {
                    // Ensure UI components are updated when song ends by itself
                    Message message = Message.obtain(null, MusicService.STOP);
                    clientMessengers.get(i).send(message);
                } catch (RemoteException e) {
                    clientMessengers.remove(i);
                }
            }
        }
    }

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MusicService.REGISTER:
                    //Log.d("psylha", "* MusicService: REGISTER Received");
                    clientMessengers.add(msg.replyTo);
                    break;
                case MusicService.UNREGISTER:
                    //Log.d("psylha", "* MusicService: UNREGISTER Received");
                    clientMessengers.remove(msg.replyTo);
                    break;
                case MusicService.START:
                    //Log.d("psylha", "* MusicService: START Received");
                    MusicService.this.loadMusic(msg.getData().getString("uri"));
                    break;
                case MusicService.PLAY:
                    //Log.d("psylha", "* MusicService: PLAY Received");
                    MusicService.this.playMusic();
                    break;
                case MusicService.PAUSE:
                    //Log.d("psylha", "* MusicService: PAUSE Received");
                    MusicService.this.pauseMusic();
                    break;
                case MusicService.STOP:
                    //Log.d("psylha", "* MusicService: STOP Received");
                    MusicService.this.stopMusic();
                    break;
            }
        }
    }
}
