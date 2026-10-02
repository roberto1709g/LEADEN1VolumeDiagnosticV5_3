package com.leaden1.volumediagnostic;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView statusView, mediaView, volumeView, keyEventView, volumeChangeView, historyView;
    private AudioManager audioManager;
    private MediaSessionManager mediaSessionManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastVolume = -1;
    private boolean receiverRegistered = false;
    private MediaController currentController;
    private final ArrayList<String> history = new ArrayList<>();

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state != null) {
                runOnUiThread(() -> {
                    String s = stateToString(state.getState());
                    updateMediaState(s);
                    log("MEDIA_STATE_" + s);
                });
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            runOnUiThread(MainActivity.this::refreshCurrentController);
        }

        @Override
        public void onSessionDestroyed() {
            runOnUiThread(() -> {
                log("MEDIA_SESSION_DESTROYED");
                currentController = null;
                refreshSessions();
            });
        }
    };

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
            if (stream == AudioManager.STREAM_MUSIC || stream == -1) {
                int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                showVolumeChange("STREAM=" + stream + " | VOL=" + cur + " (PREV=" + lastVolume + ")");
                log("VOLUME_BROADCAST " + lastVolume + " -> " + cur);
                lastVolume = cur;
                updateVolume();
            }
        }
    };

    private final Runnable volumePoller = new Runnable() {
        @Override
        public void run() {
            updateVolume();
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 48, 32, 48);

        TextView titleView = new TextView(this);
        titleView.setText("LEADEN1 Volume Diagnostic V5.3\n");
        titleView.setTextSize(18f);

        statusView = new TextView(this);
        mediaView = new TextView(this);
        volumeView = new TextView(this);
        keyEventView = new TextView(this);
        volumeChangeView = new TextView(this);

        keyEventView.setText("ÚLTIMO KEY EVENT: —");
        volumeChangeView.setText("ÚLTIMO CAMBIO: —");

        Button btnNotificationAccess = new Button(this);
        btnNotificationAccess.setText("ABRIR ACCESO A NOTIFICACIONES");
        btnNotificationAccess.setOnClickListener(v -> {
            log("ABRIENDO_ACCESO_NOTIFICACIONES");
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        Button btnRefreshSessions = new Button(this);
        btnRefreshSessions.setText("ACTUALIZAR SESIONES");
        btnRefreshSessions.setOnClickListener(v -> {
            log("MANUAL_REFRESH_SESIONES");
            refreshSessions();
        });

        Button btnClearHistory = new Button(this);
        btnClearHistory.setText("LIMPIAR HISTORIAL");
        btnClearHistory.setOnClickListener(v -> {
            history.clear();
            historyView.setText("");
        });

        TextView historyTitle = new TextView(this);
        historyTitle.setText("\nHISTORIAL");

        historyView = new TextView(this);
        historyView.setTextSize(12f);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(historyView);

        mainLayout.addView(titleView);
        mainLayout.addView(statusView);
        mainLayout.addView(mediaView);
        mainLayout.addView(volumeView);
        mainLayout.addView(keyEventView);
        mainLayout.addView(volumeChangeView);
        mainLayout.addView(btnNotificationAccess);
        mainLayout.addView(btnRefreshSessions);
        mainLayout.addView(btnClearHistory);
        mainLayout.addView(historyTitle);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);

        registerVolumeReceiver();
        handler.post(volumePoller);
        log("APP_INICIADA_V5_3");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSessions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(volumePoller);
        if (receiverRegistered) {
            try {
                unregisterReceiver(volumeReceiver);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            String name = (keyCode == KeyEvent.KEYCODE_VOLUME_UP) ? "VOL_UP" : "VOL_DOWN";
            keyEventView.setText("ÚLTIMO KEY EVENT: " + name);
            log("KEY_DOWN: " + name);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateStatus() {
        if (isListenerEnabled()) {
            statusView.setText("● ACCESO A NOTIFICACIONES ACTIVO\n");
        } else {
            statusView.setText("● ACCESO A NOTIFICACIONES NO ACTIVO\n");
        }
    }

    private void registerVolumeReceiver() {
        if (receiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            ContextCompat.registerReceiver(this, volumeReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
            receiverRegistered = true;
            log("RECEIVER_REGISTRADO_OK");
        } catch (Exception e) {
            log("REGISTER_RECEIVER_ERROR: " + e.getMessage());
        }
    }

    private void updateVolume() {
        if (audioManager == null) return;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (lastVolume != current) {
            if (lastVolume != -1) {
                log("POLL_VOL_CHANGE: " + lastVolume + " -> " + current);
            }
            lastVolume = current;
        }
        volumeView.setText("STREAM_MUSIC: " + current + " / " + max);
    }

    private void refreshSessions() {
        updateStatus();
        if (!isListenerEnabled()) {
            mediaView.setText("MEDIA SESSION: ACCESO NO DISPONIBLE\nActiva el acceso de notificaciones.");
            return;
        }
        try {
            List<MediaController> sessions = mediaSessionManager.getActiveSessions(getListenerComponent());
            log("SESIONES_ENCONTRADAS=" + sessions.size());
            updateSessions(sessions);
        } catch (SecurityException e) {
            mediaView.setText("MEDIA SESSION: SECURITY_EXCEPTION\n" + e.getMessage());
            log("SECURITY_EXCEPTION: " + e.getMessage());
        } catch (Exception e) {
            mediaView.setText("MEDIA SESSION: ERROR\n" + e.getMessage());
            log("SESSION_ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void updateSessions(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            currentController = null;
            mediaView.setText("MEDIA SESSION: NINGUNA ACTIVA");
            return;
        }
        MediaController selected = null;
        for (MediaController c : sessions) {
            PlaybackState st = c.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                selected = c;
                break;
            }
        }
        if (selected == null) selected = sessions.get(0);

        if (currentController != selected) {
            if (currentController != null) {
                try {
                    currentController.unregisterCallback(mediaCallback);
                } catch (Exception ignored) {}
            }
            currentController = selected;
            try {
                currentController.registerCallback(mediaCallback);
            } catch (Exception e) {
                log("CALLBACK_ERROR: " + e.getMessage());
            }
        }
        refreshCurrentController();
    }

    private void refreshCurrentController() {
        if (currentController == null) {
            mediaView.setText("MEDIA SESSION: NINGUNA ACTIVA");
            return;
        }
        String pkg = currentController.getPackageName();
        PlaybackState state = currentController.getPlaybackState();
        String stateStr = state == null ? "UNKNOWN" : stateToString(state.getState());

        String title = "—";
        String artist = "—";
        if (currentController.getMetadata() != null) {
            CharSequence t = currentController.getMetadata().getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = currentController.getMetadata().getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (t != null) title = t.toString();
            if (a != null) artist = a.toString();
        }

        mediaView.setText("MEDIA SESSION: " + pkg + "\nTÍTULO: " + title + "\nARTISTA: " + artist + "\nESTADO: " + stateStr);
    }

    private void updateMediaState(String stateStr) {
        if (currentController == null) return;
        String pkg = currentController.getPackageName();
        String title = "—";
        if (currentController.getMetadata() != null) {
            CharSequence t = currentController.getMetadata().getText(MediaMetadata.METADATA_KEY_TITLE);
            if (t != null) title = t.toString();
        }
        mediaView.setText("MEDIA SESSION: " + pkg + "\nTÍTULO: " + title + "\nESTADO: " + stateStr);
    }

    private boolean isListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private ComponentName getListenerComponent() {
        return new ComponentName(this, MediaNotificationListener.class);
    }

    private void showVolumeChange(String info) {
        volumeChangeView.setText("ÚLTIMO CAMBIO: " + info);
    }

    private String stateToString(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            default: return "STATE_" + state;
        }
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        history.add(0, time + "  " + msg);
        StringBuilder sb = new StringBuilder();
        for (String item : history) {
            sb.append(item).append("\n");
        }
        if (historyView != null) {
            historyView.setText(sb.toString());
        }
    }
}
