package com.example.zylogi_motoristas;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText editTextCpf;
    private EditText editTextSenha;
    private MaterialButton loginButton;
    private MaterialButton biometricButton;
    private ProgressBar progressBar;

    private LoginViewModel loginViewModel;
    private AuthSessionManager authSessionManager;

    // Componentes para a biometria
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== onCreate iniciado ===");
        setContentView(R.layout.activity_login);

        // 1. Inicializa os componentes principais
        loginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        authSessionManager = new AuthSessionManager(this);
        Log.d(TAG, "AuthSessionManager criado");

        // 2. Associa os componentes da tela e configura os listeners
        setupViews();
        Log.d(TAG, "Views configuradas");

        // 3. Configura o prompt biométrico
        setupBiometrics();
        Log.d(TAG, "Biometria configurada");

        // 4. Observa as mudanças do ViewModel
        observeViewModel();
        Log.d(TAG, "ViewModel observado");

        // 5. Verifica se deve mostrar opção de biometria
        checkAndSetupBiometricOption();
        Log.d(TAG, "=== onCreate finalizado ===");
    }

    private void setupViews() {
        Log.d(TAG, "=== setupViews iniciado ===");
        
        editTextCpf = findViewById(R.id.editTextCpf);
        Log.d(TAG, "editTextCpf encontrado: " + (editTextCpf != null));
        
        editTextSenha = findViewById(R.id.editTextSenha);
        Log.d(TAG, "editTextSenha encontrado: " + (editTextSenha != null));
        
        loginButton = findViewById(R.id.loginButton);
        Log.d(TAG, "loginButton encontrado: " + (loginButton != null));
        
        biometricButton = findViewById(R.id.biometricButton);
        Log.d(TAG, "biometricButton encontrado: " + (biometricButton != null));
        
        progressBar = findViewById(R.id.progressBar);
        Log.d(TAG, "progressBar encontrado: " + (progressBar != null));

        loginButton.setOnClickListener(v -> performLogin());
        biometricButton.setOnClickListener(v -> showBiometricPrompt());
        
        Log.d(TAG, "=== setupViews finalizado ===");
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
                String cpf = editTextCpf.getText().toString().trim();

                Log.d(TAG, "=== Login bem-sucedido ===");
                Log.d(TAG, "Token recebido: " + (token != null ? "sim" : "não"));
                Log.d(TAG, "CPF: ***");

                // Salva o token usando o AuthSessionManager
                authSessionManager.saveAuthToken(token);
                Log.d(TAG, "Token salvo");

                // Habilita biometria para próximos logins se o dispositivo suportar
                boolean biometricSupported = isBiometricSupported();
                boolean biometricAlreadyEnabled = authSessionManager.isBiometricEnabled();
                
                Log.d(TAG, "Biometria suportada: " + biometricSupported);
                Log.d(TAG, "Biometria já habilitada: " + biometricAlreadyEnabled);
                
                if (biometricSupported && !biometricAlreadyEnabled) {
                    authSessionManager.enableBiometric(cpf);
                    Log.d(TAG, "Biometria habilitada para próximos logins");
                } else {
                    Log.d(TAG, "Biometria não foi habilitada - suportada: " + biometricSupported + ", já habilitada: " + biometricAlreadyEnabled);
                }

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

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    // Usuário cancelou, não fazer nada
                    return;
                }
                Toast.makeText(getApplicationContext(), 
                    "Erro na autenticação: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), 
                    "Autenticação falhou. Tente novamente.", Toast.LENGTH_SHORT).show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Login Biométrico")
                .setSubtitle("Use sua digital ou padrão para acessar o app")
                .setDescription("Coloque o dedo no sensor ou desenhe seu padrão")
                .setNegativeButtonText("Cancelar")
                .build();
    }

    private void checkAndSetupBiometricOption() {
        Log.d(TAG, "=== Verificando configuração de biometria ===");
        
        String token = authSessionManager.getAuthToken();
        boolean biometricEnabled = authSessionManager.isBiometricEnabled();
        boolean biometricSupported = isBiometricSupported();
        
        Log.d(TAG, "Token existe: " + (token != null));
        Log.d(TAG, "Biometria habilitada: " + biometricEnabled);
        Log.d(TAG, "Biometria suportada: " + biometricSupported);
        
        // Mostra o botão biométrico se a biometria for suportada
        if (biometricSupported) {
            Log.d(TAG, "Biometria suportada - mostrando botão de biometria");
            biometricButton.setVisibility(View.VISIBLE);
            
            // Se já tem token e biometria habilitada, faz login automático
            if (token != null && biometricEnabled) {
                Log.d(TAG, "Login automático por biometria disponível");
                
                // Preenche automaticamente o CPF salvo
                String savedCpf = authSessionManager.getSavedUserCpf();
                if (savedCpf != null && !savedCpf.isEmpty()) {
                    editTextCpf.setText(savedCpf);
                    Log.d(TAG, "CPF preenchido automaticamente");
                }
                
                // Mostra automaticamente o prompt biométrico
                showBiometricPrompt();
            } else {
                Log.d(TAG, "Biometria disponível para configuração no primeiro login");
            }
        } else {
            Log.d(TAG, "Biometria não suportada - botão permanece oculto");
            biometricButton.setVisibility(View.GONE);
        }
    }

    private boolean isBiometricSupported() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK | 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );
        
        Log.d(TAG, "Resultado da verificação biométrica: " + result);
        Log.d(TAG, "BIOMETRIC_SUCCESS = " + BiometricManager.BIOMETRIC_SUCCESS);
        
        boolean supported = result == BiometricManager.BIOMETRIC_SUCCESS;
        Log.d(TAG, "Biometria suportada: " + supported);
        
        return supported;
    }

    private void showBiometricPrompt() {
        Log.d(TAG, "=== showBiometricPrompt chamado ===");
        
        // Verifica se há credenciais salvas
        String savedCpf = authSessionManager.getSavedUserCpf();
        String token = authSessionManager.getAuthToken();
        
        if (savedCpf == null || savedCpf.isEmpty() || token == null) {
            Log.d(TAG, "Sem credenciais salvas - solicitando login manual primeiro");
            Toast.makeText(this, "Faça login uma vez para habilitar a biometria", Toast.LENGTH_LONG).show();
            return;
        }
        
        Log.d(TAG, "Credenciais encontradas - iniciando autenticação biométrica");
        if (isBiometricSupported()) {
            biometricPrompt.authenticate(promptInfo);
        } else {
            Toast.makeText(this, "Biometria não disponível neste dispositivo", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToMainApp() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}