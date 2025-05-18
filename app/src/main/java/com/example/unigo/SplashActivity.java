package com.example.unigo;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean transitioned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        VideoView vv = findViewById(R.id.videoSplash);

        // Forzar fondo blanco y transparencia en la Surface
        vv.setBackgroundColor(Color.WHITE);
        SurfaceHolder holder = vv.getHolder();
        holder.setFormat(PixelFormat.TRANSLUCENT);
        vv.setZOrderOnTop(true);

        Uri video = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.splash_anim);
        vv.setVideoURI(video);

        // 1) Cuando el vídeo esté preparado, lo arrancamos, aplicamos crop y programamos el fallback
        vv.setOnPreparedListener(mp -> {
            // Escalado “crop” para evitar barras negras
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);

            int duration = mp.getDuration(); // ms
            vv.start();

            // Fallback: por si OnCompletion no se dispara
            handler.postDelayed(this::goToMain, duration + 100);
        });

        // 2) También escuchamos OnCompletionListener
        vv.setOnCompletionListener(mp -> goToMain());
    }

    private void goToMain() {
        if (transitioned) return;
        transitioned = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        // bloqueamos atrás en splash
        super.onBackPressed();
    }
}
