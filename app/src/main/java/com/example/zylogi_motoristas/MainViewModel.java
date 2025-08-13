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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                    List<Pickup> allPickupsFromAPI = response.body();

                    // NOVA FILTRAGEM: Primeiro filtra por data de agendamento para hoje
                    List<Pickup> todayScheduledPickups = allPickupsFromAPI.stream()
                            .filter(p -> isScheduledForToday(p.getScheduledDate()))
                            .collect(Collectors.toList());

                    // Depois filtra para mostrar apenas as pendentes no carrossel
                    List<Pickup> pendingPickups = todayScheduledPickups.stream()
                            .filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()))
                            .collect(Collectors.toList());
                    _openPickups.setValue(pendingPickups);

                    // Calcula o progresso com base nas coletas agendadas para hoje
                    calculateProgress(todayScheduledPickups);
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
        // Para chamadas simples, enviar apenas o status
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        
        apiService.finalizePickup(pickupId, updates).enqueue(new Callback<Pickup>() {
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

    // Método para finalização com detalhes do motorista
    public void finalizePickupWithDetails(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl) {
        _isLoading.setValue(true);
        
        // Logs de debug
        android.util.Log.d("MainViewModel", "=== FINALIZANDO COLETA COM DETALHES ===");
        android.util.Log.d("MainViewModel", "Pickup ID: " + pickup.getId());
        android.util.Log.d("MainViewModel", "Status: COMPLETED");
        android.util.Log.d("MainViewModel", "Observação: " + observationDriver);
        android.util.Log.d("MainViewModel", "Occurrence ID: " + occurrenceId);
        android.util.Log.d("MainViewModel", "Driver Attachment: " + (driverAttachmentUrl != null && !driverAttachmentUrl.isEmpty() ? "Presente" : "Ausente"));
        
        // Criar Map com todos os dados do motorista
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "COMPLETED");
        if (observationDriver != null && !observationDriver.trim().isEmpty()) {
            updates.put("observationDriver", observationDriver);
        }
        if (occurrenceId != null && !occurrenceId.trim().isEmpty()) {
            updates.put("occurrenceId", occurrenceId);
        }
        if (driverAttachmentUrl != null && !driverAttachmentUrl.trim().isEmpty()) {
            updates.put("driverAttachmentUrl", driverAttachmentUrl);
        }
        
        // Enviar apenas os campos que têm valores
        apiService.finalizePickup(pickup.getId(), updates)
                .enqueue(new Callback<Pickup>() {
                    @Override
                    public void onResponse(Call<Pickup> call, Response<Pickup> response) {
                        if (response.isSuccessful()) {
                            android.util.Log.d("MainViewModel", "Coleta finalizada com sucesso - apenas status enviado");
                            _updateResult.setValue("Coleta finalizada com sucesso!");
                            fetchPickups(); // ESSENCIAL: Busca os dados atualizados do servidor
                        } else {
                            android.util.Log.e("MainViewModel", "Falha ao finalizar coleta: " + response.code());
                            _updateResult.setValue("Falha ao finalizar a coleta: " + response.code());
                            _isLoading.setValue(false);
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<Pickup> call, Throwable t) {
                        android.util.Log.e("MainViewModel", "Erro de conexão: " + t.getMessage());
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

    // NOVO MÉTODO: Verifica se uma coleta está agendada para hoje
    private boolean isScheduledForToday(String scheduledDate) {
        if (scheduledDate == null || scheduledDate.trim().isEmpty()) {
            // Se não há data de agendamento, considera como agendada para hoje (compatibilidade)
            return true;
        }

        try {
            String today = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // Compara apenas a parte da data (ignora horário se houver)
            String dateOnly = scheduledDate.substring(0, Math.min(scheduledDate.length(), 10));
            return today.equals(dateOnly);
        } catch (Exception e) {
            // Em caso de erro na formatação, considera como agendada para hoje
            return true;
        }
    }
}