package com.example.zylogi_motoristas;// Local: app/src/main/java/com/example/zylogi_motoristas/LoginViewModel.java

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginViewModel extends ViewModel {

    private final MutableLiveData<LoginResponse> _loginResult = new MutableLiveData<>();
    public LiveData<LoginResponse> loginResult = _loginResult;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final ApiService apiService;

    public LoginViewModel() {
        apiService = RetrofitClient.getClient().create(ApiService.class);
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