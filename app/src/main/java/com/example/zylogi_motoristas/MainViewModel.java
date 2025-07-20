package com.example.zylogi_motoristas;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.auth0.android.jwt.JWT;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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

    private final MutableLiveData<Integer> _progressPercentage = new MutableLiveData<>(0);
    public LiveData<Integer> progressPercentage = _progressPercentage;

    private final MutableLiveData<String> _progressSummary = new MutableLiveData<>("Nenhuma coleta hoje");
    public LiveData<String> progressSummary = _progressSummary;

    private AuthSessionManager authSessionManager;
    private ApiService apiService;

    public MainViewModel(@NonNull Application application) {
        super(application);
        authSessionManager = new AuthSessionManager(application);
        apiService = RetrofitClient.getClient(application).create(ApiService.class);
    }

    // O MÉTODO QUE ESTAVA FALTANDO
    public void fetchPickups() {
        _isLoading.setValue(true);
        String token = authSessionManager.getAuthToken();
        if (token == null) {
            _error.setValue("Sessão inválida. Faça login novamente.");
            _isLoading.setValue(false);
            return;
        }
        JWT jwt = new JWT(token);
        String driverId = jwt.getSubject();
        if (driverId == null) {
            _error.setValue("Não foi possível identificar o motorista no token.");
            _isLoading.setValue(false);
            return;
        }

        String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        apiService.getPickups(driverId, today, today).enqueue(new Callback<List<Pickup>>() {
            @Override
            public void onResponse(Call<List<Pickup>> call, Response<List<Pickup>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Pickup> pickupList = response.body();
                    _pickups.setValue(pickupList);
                    calculateProgress(pickupList);
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

    private void calculateProgress(List<Pickup> pickups) {
        if (pickups == null || pickups.isEmpty()) {
            _progressPercentage.setValue(0);
            _progressSummary.setValue("Nenhuma coleta para hoje");
            return;
        }

        int total = pickups.size();
        long completed = pickups.stream().filter(p -> "COLLECTED".equalsIgnoreCase(p.getStatus())).count();

        int percentage = (int) (((double) completed / total) * 100);
        _progressPercentage.setValue(percentage);

        String summary = String.format(Locale.getDefault(), "Coletas concluídas: %d de %d", completed, total);
        _progressSummary.setValue(summary);
    }
}