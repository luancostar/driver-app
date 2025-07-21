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
import java.util.stream.Collectors; // Importe
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainViewModel extends AndroidViewModel {

    // LiveData para a lista de coletas ABERTAS (para o carrossel)
    private final MutableLiveData<List<Pickup>> _openPickups = new MutableLiveData<>();
    public LiveData<List<Pickup>> openPickups = _openPickups;

    // LiveDatas existentes
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public LiveData<Boolean> isLoading = _isLoading;
    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;
    private final MutableLiveData<String> _updateResult = new MutableLiveData<>();
    public LiveData<String> updateResult = _updateResult;

    // LiveDatas para o progresso
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

    public void fetchPickups() {
        _isLoading.setValue(true);
        String token = authSessionManager.getAuthToken();
        if (token == null) { /* ... */ return; }
        JWT jwt = new JWT(token);
        String driverId = jwt.getSubject();
        if (driverId == null) { /* ... */ return; }

        String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // A chamada da API continua buscando TODAS as coletas do dia
        apiService.getPickups(driverId, today, today).enqueue(new Callback<List<Pickup>>() {
            @Override
            public void onResponse(Call<List<Pickup>> call, Response<List<Pickup>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Pickup> allPickupsForTheDay = response.body();

                    // Filtra a lista para mostrar apenas as pendentes no carrossel
                    List<Pickup> pendingPickups = allPickupsForTheDay.stream()
                            .filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()))
                            .collect(Collectors.toList());
                    _openPickups.setValue(pendingPickups);

                    // Calcula o progresso com base na lista COMPLETA
                    calculateProgress(allPickupsForTheDay);
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

    // O método de finalização continua o mesmo, pois ele chama fetchPickups() no sucesso,
    // o que já dispara a nova filtragem e cálculo de progresso.
    public void finalizePickup(String pickupId, String status) {
        _isLoading.setValue(true);
        apiService.finalizePickup(pickupId, status).enqueue(new Callback<Pickup>() {
            @Override
            public void onResponse(Call<Pickup> call, Response<Pickup> response) {
                if (response.isSuccessful()) {
                    _updateResult.setValue("Coleta atualizada com sucesso!");
                    fetchPickups(); // ESSENCIAL: Busca os dados atualizados do servidor
                } else {
                    _updateResult.setValue("Falha ao atualizar a coleta: " + response.code());
                    _isLoading.setValue(false);
                }
            }
            @Override
            public void onFailure(Call<Pickup> call, Throwable t) {
                _updateResult.setValue("Erro de conexão: " + t.getMessage());
                _isLoading.setValue(false);
            }
        });
    }

    // O cálculo de progresso agora considera "COMPLETED" e "NOT_COMPLETED" como finalizadas
    private void calculateProgress(List<Pickup> allPickups) {
        if (allPickups == null || allPickups.isEmpty()) {
            _progressPercentage.setValue(0);
            _progressSummary.setValue("Nenhuma coleta para hoje");
            return;
        }

        int total = allPickups.size();
        // Conta como "concluída" qualquer coleta que não esteja mais pendente
        long concluded = allPickups.stream()
                .filter(p -> !"PENDING".equalsIgnoreCase(p.getStatus()))
                .count();

        int percentage = (int) (((double) concluded / total) * 100);
        _progressPercentage.setValue(percentage);

        String summary = String.format(Locale.getDefault(), "Coletas concluídas: %d de %d", concluded, total);
        _progressSummary.setValue(summary);
    }
}