package com.example.zylogi_motoristas;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.auth0.android.jwt.JWT;

import java.time.LocalDate;
import java.time.ZoneId; // Importe o ZoneId
import java.time.format.DateTimeFormatter;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Pickup>> _pickups = new MutableLiveData<>();
    public LiveData<List<Pickup>> pickups = _pickups;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private AuthSessionManager authSessionManager;
    private ApiService apiService;

    public MainViewModel(@NonNull Application application) {
        super(application);
        authSessionManager = new AuthSessionManager(application);
        apiService = RetrofitClient.getClient(application).create(ApiService.class);
    }

    public void fetchPickups() {
        _isLoading.setValue(true);
        String token = authSessionManager.getAuthToken();

        if (token == null) {
            _isLoading.setValue(false);
            _error.setValue("Sessão inválida. Por favor, faça login novamente.");
            return;
        }

        JWT jwt = new JWT(token);
        String driverId = jwt.getSubject();

        if (driverId == null) {
            _isLoading.setValue(false);
            _error.setValue("Não foi possível identificar o motorista no token.");
            return;
        }

        // CÓDIGO DE DATA CORRIGIDO E SIMPLIFICADO
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(formatter);

        // Fazer a chamada para a API
        apiService.getPickups(driverId, today, today).enqueue(new Callback<List<Pickup>>() {
            @Override
            public void onResponse(Call<List<Pickup>> call, Response<List<Pickup>> response) {
                if (response.isSuccessful()) {
                    _pickups.setValue(response.body());
                } else {
                    _error.setValue("Erro ao buscar coletas: " + response.code());
                }
                _isLoading.setValue(false);
            }

            @Override
            public void onFailure(Call<List<Pickup>> call, Throwable t) {
                _error.setValue("Falha na rede: " + t.getMessage());
                _isLoading.setValue(false);
            }
        });
    }
}