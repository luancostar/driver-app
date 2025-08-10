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

    // Novo método para finalização com detalhes completos
    public void finalizePickupWithDetails(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl) {
        _isLoading.setValue(true);
        
        // Primeiro, buscar os detalhes completos da coleta para obter pickupRouteId e vehicleId
        android.util.Log.d("MainViewModel", "Buscando detalhes completos da coleta: " + pickup.getId());
        
        apiService.getPickupById(pickup.getId()).enqueue(new Callback<Pickup>() {
            @Override
            public void onResponse(Call<Pickup> call, Response<Pickup> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Pickup fullPickup = response.body();
                    android.util.Log.d("MainViewModel", "Detalhes da coleta obtidos com sucesso");
                    
                    // Prosseguir com a finalização usando os dados completos
                    proceedWithFinalization(fullPickup, observationDriver, occurrenceId, driverAttachmentUrl);
                } else {
                    android.util.Log.w("MainViewModel", "Falha ao obter detalhes da coleta, usando dados originais");
                    // Se falhar, usar os dados originais
                    proceedWithFinalization(pickup, observationDriver, occurrenceId, driverAttachmentUrl);
                }
            }
            
            @Override
            public void onFailure(Call<Pickup> call, Throwable t) {
                android.util.Log.w("MainViewModel", "Erro ao buscar detalhes da coleta: " + t.getMessage());
                // Se falhar, usar os dados originais
                proceedWithFinalization(pickup, observationDriver, occurrenceId, driverAttachmentUrl);
            }
        });
    }
    
    private void proceedWithFinalization(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl) {
        // Obter driverId do JWT token
        String token = authSessionManager.getAuthToken();
        String driverId = null;
        String vehicleIdFromJWT = null;
        if (token != null) {
            JWT jwt = new JWT(token);
            driverId = jwt.getSubject();
            
            // Verificar se existe vehicleId no JWT
            try {
                vehicleIdFromJWT = jwt.getClaim("vehicleId").asString();
                android.util.Log.d("MainViewModel", "Vehicle ID do JWT: " + vehicleIdFromJWT);
            } catch (Exception e) {
                android.util.Log.d("MainViewModel", "Vehicle ID não encontrado no JWT");
            }
            
            // Log de todas as claims para debug
            android.util.Log.d("MainViewModel", "JWT Claims disponíveis: " + jwt.getClaims().keySet());
        }
        
        // Logs de debug para rastrear os valores recebidos
        android.util.Log.d("MainViewModel", "=== CHAMADA API ===");
        android.util.Log.d("MainViewModel", "Pickup ID: " + pickup.getId());
        android.util.Log.d("MainViewModel", "Status: COMPLETED");
        android.util.Log.d("MainViewModel", "Observation Driver: '" + observationDriver + "'");
        android.util.Log.d("MainViewModel", "Occurrence ID: '" + occurrenceId + "'");
        android.util.Log.d("MainViewModel", "Driver Attachment URL: " + (driverAttachmentUrl != null && !driverAttachmentUrl.isEmpty() ? "Presente" : "Vazio"));
        android.util.Log.d("MainViewModel", "Driver ID: " + driverId);
        android.util.Log.d("MainViewModel", "Vehicle ID (do Pickup): " + pickup.getVehicleId());
        android.util.Log.d("MainViewModel", "Vehicle ID (do JWT): " + vehicleIdFromJWT);
        
        // Decidir qual método usar baseado na disponibilidade dos campos opcionais
        String vehicleId = pickup.getVehicleId();
        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            vehicleId = vehicleIdFromJWT;
        }
        
        String pickupRouteId = pickup.getPickupRouteId();
        
        android.util.Log.d("MainViewModel", "Vehicle ID (final): " + vehicleId);
        android.util.Log.d("MainViewModel", "Pickup Route ID: " + pickupRouteId);
        
        boolean hasVehicleId = vehicleId != null && !vehicleId.trim().isEmpty();
        boolean hasPickupRouteId = pickupRouteId != null && !pickupRouteId.trim().isEmpty();
        
        Call<Pickup> call;
        if (hasVehicleId || hasPickupRouteId) {
            // Se temos pelo menos um dos campos opcionais, usar o método completo
            android.util.Log.d("MainViewModel", "Usando método completo (com campos opcionais)");
            call = apiService.finalizePickupWithDetails(pickup.getId(), "COMPLETED", observationDriver, occurrenceId, driverAttachmentUrl, driverId, vehicleId, pickupRouteId);
        } else {
            // Se não temos nenhum campo opcional, usar o método básico para evitar enviar nulls
            android.util.Log.d("MainViewModel", "Usando método básico (sem campos opcionais para evitar nulls)");
            call = apiService.finalizePickupBasic(pickup.getId(), "COMPLETED", observationDriver, occurrenceId, driverAttachmentUrl, driverId);
        }
        
        call.enqueue(new Callback<Pickup>() {
            @Override
            public void onResponse(Call<Pickup> call, Response<Pickup> response) {
                if (response.isSuccessful()) {
                    _updateResult.setValue("Coleta finalizada com sucesso!");
                    fetchPickups(); // ESSENCIAL: Busca os dados atualizados do servidor
                } else {
                    _updateResult.setValue("Falha ao finalizar a coleta: " + response.code());
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