package com.example.zylogi_motoristas;

import android.app.Application; // Importe
import androidx.annotation.NonNull; // Importe
import androidx.lifecycle.AndroidViewModel; // Mude de ViewModel para AndroidViewModel
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginViewModel extends AndroidViewModel { // MUDANÇA AQUI

    private final MutableLiveData<LoginResponse> _loginResult = new MutableLiveData<>();
    public LiveData<LoginResponse> loginResult = _loginResult;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private ApiService apiService;

    // O construtor agora recebe o contexto da aplicação
    public LoginViewModel(@NonNull Application application) {
        super(application);
        // E o passa para o RetrofitClient
        apiService = RetrofitClient.getClient(application).create(ApiService.class);
    }

    public void login(String cpf, String password) {
        _isLoading.setValue(true);

        LoginRequest request = new LoginRequest(cpf, password);
        apiService.login(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    _loginResult.setValue(response.body());
                } else {
                    _error.setValue("CPF ou Senha inválidos.");
                }
                _isLoading.setValue(false);
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                _error.setValue("Falha na conexão. Verifique a internet.");
                _isLoading.setValue(false);
            }
        });
    }
}