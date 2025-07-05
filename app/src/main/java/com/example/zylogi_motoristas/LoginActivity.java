package com.example.zylogi_motoristas;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.zylogi_motoristas.LoginViewModel;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {

    private EditText editTextCpf;
    private EditText editTextSenha;
    private Button loginButton;
    private ProgressBar progressBar;
    private LoginViewModel loginViewModel; // A referência para o nosso ViewModel

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Pega uma instância do ViewModel. O Android gerencia o ciclo de vida dele.
        loginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        // 2. Associa os componentes da tela (Views)
        editTextCpf = findViewById(R.id.editTextCpf);
        editTextSenha = findViewById(R.id.editTextSenha);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);

        // 3. Configura o clique do botão para chamar o ViewModel
        loginButton.setOnClickListener(v -> {
            String cpf = editTextCpf.getText().toString().trim();
            // Lembre-se que o backend espera 'password'
            String password = editTextSenha.getText().toString().trim();

            if (TextUtils.isEmpty(cpf) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Por favor, preencha todos os campos.", Toast.LENGTH_SHORT).show();
                return;
            }
            // A Activity apenas notifica o ViewModel, ela não sabe fazer o login
            loginViewModel.login(cpf, password);
        });

        // 4. Começa a "ouvir" as respostas do ViewModel
        observeViewModel();
    }

    private void observeViewModel() {
        // Observa o estado de carregamento (para mostrar/esconder a ProgressBar)
        loginViewModel.isLoading.observe(this, isLoading -> {
            if (isLoading) {
                progressBar.setVisibility(View.VISIBLE);
                loginButton.setEnabled(false); // Desativa o botão enquanto carrega
            } else {
                progressBar.setVisibility(View.GONE);
                loginButton.setEnabled(true); // Reativa o botão
            }
        });

        // Observa as mensagens de erro
        loginViewModel.error.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        // Observa o resultado de sucesso do login
        loginViewModel.loginResult.observe(this, loginResponse -> {
            if (loginResponse != null) {
                Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show();
                String token = loginResponse.getAccessToken();

                // TODO: Salvar o token (usando SharedPreferences) antes de navegar

                // Exemplo de navegação para uma futura MainActivity
                // Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                // startActivity(intent);
                // finish(); // Fecha a LoginActivity para o usuário não voltar para ela
            }
        });
    }
}