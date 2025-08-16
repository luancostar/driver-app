package com.example.zylogi_motoristas;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.zylogi_motoristas.R;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIMER = 3500; // Tempo aumentado para 3.5 segundos
    
    private ImageView logoImageView;
    private TextView sloganText;
    private View floatingElement1;
    private View floatingElement2;
    private View floatingElement3;
    private ProgressBar loadingIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Inicializar views
        logoImageView = findViewById(R.id.logoImageView);
        sloganText = findViewById(R.id.sloganText);
        floatingElement1 = findViewById(R.id.floatingElement1);
        floatingElement2 = findViewById(R.id.floatingElement2);
        floatingElement3 = findViewById(R.id.floatingElement3);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        // Iniciar animações
        startModernAnimations();

        // Navegar após o tempo definido
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                    // Adicionar transição suave
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } catch (Exception e) {
                    // Log do erro para debug
                    android.util.Log.e("SplashActivity", "Erro ao navegar para LoginActivity", e);
                    // Tentar novamente sem transição
                    try {
                        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    } catch (Exception e2) {
                        android.util.Log.e("SplashActivity", "Erro crítico na navegação", e2);
                        // Em último caso, fechar o app
                        finishAffinity();
                    }
                }
            }
        }, SPLASH_TIMER);
    }

    private void startModernAnimations() {
        // Animações dos elementos flutuantes do background
        startFloatingBackgroundAnimations();

        // Animação moderna da logo
        Animation logoEntrance = AnimationUtils.loadAnimation(this, R.anim.logo_entrance);
        logoImageView.startAnimation(logoEntrance);

        // Efeito typewriter para o texto
        startTypewriterEffect();

        // Animação do loading indicator
        loadingIndicator.setAlpha(0f);
        loadingIndicator.animate()
                .alpha(1f)
                .setDuration(800)
                .setStartDelay(2000)
                .start();
    }

    private void startFloatingBackgroundAnimations() {
        // Animação para o círculo flutuante
        Animation floatingAnim1 = AnimationUtils.loadAnimation(this, R.anim.background_floating_animation);
        floatingElement1.startAnimation(floatingAnim1);
        
        // Animação para o quadrado geométrico
        Animation geometricAnim1 = AnimationUtils.loadAnimation(this, R.anim.background_geometric_animation);
        floatingElement2.startAnimation(geometricAnim1);
        
        // Animação para o triângulo com delay
        Animation floatingAnim2 = AnimationUtils.loadAnimation(this, R.anim.background_floating_animation);
        floatingElement3.postDelayed(new Runnable() {
            @Override
            public void run() {
                floatingElement3.startAnimation(floatingAnim2);
            }
        }, 500); // Delay de 500ms para criar efeito escalonado
    }

    private void startTypewriterEffect() {
        final String fullText = "transparência em movimento";
        sloganText.setText("");
        sloganText.setAlpha(0f);
        
        // Fade in do TextView primeiro
        sloganText.animate()
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(1200)
                .start();

        // Efeito typewriter - corrigido para incluir todos os caracteres
        ValueAnimator typewriterAnimator = ValueAnimator.ofInt(0, fullText.length() + 1);
        typewriterAnimator.setDuration(1800);
        typewriterAnimator.setStartDelay(1400);
        
        typewriterAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int currentLength = (int) animation.getAnimatedValue();
                // Garantir que não exceda o comprimento do texto
                if (currentLength <= fullText.length()) {
                    String currentText = fullText.substring(0, currentLength);
                    sloganText.setText(currentText);
                }
            }
        });
        
        typewriterAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Garantir que o texto completo seja exibido
                sloganText.setText(fullText);
                
                // Aplicar efeito gradiente moderno ao texto
                applyModernGradientEffect();
                
                // Aplicar animação final ao texto
                Animation textAnimation = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.text_typewriter);
                sloganText.startAnimation(textAnimation);
            }
        });
        
        typewriterAnimator.start();
    }
    
    private void applyModernGradientEffect() {
        // Aplicar animação de brilho gradiente moderno
        Animation gradientGlow = AnimationUtils.loadAnimation(this, R.anim.text_gradient_glow);
        sloganText.startAnimation(gradientGlow);
        
        // Efeito adicional de escala suave
        sloganText.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(400)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        sloganText.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(400)
                                .start();
                    }
                })
                .start();
    }
}