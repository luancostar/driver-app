package com.example.zylogi_motoristas;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.example.zylogi_motoristas.R;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIMER = 2500; // Tempo em milissegundos (2.5 segundos)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 1. Encontrar a ImageView e carregar a animação
        ImageView logoImageView = findViewById(R.id.logoImageView);
        Animation fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        logoImageView.startAnimation(fadeInAnimation);

        // 2. Criar um Handler para esperar e depois navegar
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // 3. Criar a Intent para a LoginActivity
                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                startActivity(intent);

                // 4. Finalizar a SplashActivity para que o usuário não possa voltar
                finish();
            }
        }, SPLASH_TIMER);
    }
}