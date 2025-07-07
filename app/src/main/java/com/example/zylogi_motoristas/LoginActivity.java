package com.example.zylogi_motoristas;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    private EditText editTextCpf;
    private EditText editTextSenha;
    private Button loginButton;
    private ProgressBar progressBar;

    private LoginViewModel loginViewModel;
    // Usando o nome corrigido para evitar conflito
    private AuthSessionManager authSessionManager;

    // Componentes para a biometria
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Inicializa os componentes principais
        loginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        authSessionManager = new AuthSessionManager(this); // Crie a instância aqui

        // 2. Associa os componentes da tela e configura os listeners
        setupViews();

        // 3. Configura o prompt biométrico
        setupBiometrics();

        // 4. Observa as mudanças do ViewModel
        observeViewModel();

        // 5. LÓGICA PRINCIPAL: Se já existe um token, tenta o login biométrico
        if (authSessionManager.getAuthToken() != null) {
            checkBiometricSupportAndShowPrompt();
        }
    }

    private void setupViews() {
        editTextCpf = findViewById(R.id.editTextCpf);
        editTextSenha = findViewById(R.id.editTextSenha);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);

        loginButton.setOnClickListener(v -> performLogin());
    }

    private void performLogin() {
        String cpf = editTextCpf.getText().toString().trim();
        String password = editTextSenha.getText().toString().trim();

        if (TextUtils.isEmpty(cpf) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Por favor, preencha todos os campos.", Toast.LENGTH_SHORT).show();
            return;
        }
        loginViewModel.login(cpf, password);
    }

    private void observeViewModel() {
        loginViewModel.isLoading.observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            loginButton.setEnabled(!isLoading);
        });

        loginViewModel.error.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        loginViewModel.loginResult.observe(this, loginResponse -> {
            if (loginResponse != null) {
                Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show();
                String token = loginResponse.getAccessToken();

                // Salva o token usando o AuthSessionManager
                authSessionManager.saveAuthToken(token);

                // Navega para a tela principal
                navigateToMainApp();
            }
        });
    }

    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(getApplicationContext(), "Autenticação bem-sucedida!", Toast.LENGTH_SHORT).show();
                navigateToMainApp();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Login Biométrico")
                .setSubtitle("Use sua digital para acessar o app")
                .setNegativeButtonText("Usar CPF e Senha")
                .build();
    }

    private void checkBiometricSupportAndShowPrompt() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void navigateToMainApp() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}