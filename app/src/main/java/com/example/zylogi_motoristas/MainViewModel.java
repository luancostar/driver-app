package com.example.zylogi_motoristas;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.auth0.android.jwt.JWT;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

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
        
        // Adicionar data e hora de finalização (horário local do Brasil)
        String completionDate = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        updates.put("completionDate", completionDate);
        
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
    public void finalizePickupWithDetails(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl, Integer driverNumberPackages) {
        _isLoading.setValue(true);
        
        // Logs de debug
        android.util.Log.d("MainViewModel", "=== FINALIZANDO COLETA COM DETALHES ===");
        android.util.Log.d("MainViewModel", "Pickup ID: " + pickup.getId());
        android.util.Log.d("MainViewModel", "Status: COMPLETED");
        android.util.Log.d("MainViewModel", "Observação: " + observationDriver);
        android.util.Log.d("MainViewModel", "Occurrence ID: " + occurrenceId);
        android.util.Log.d("MainViewModel", "Driver Attachment: " + (driverAttachmentUrl != null && !driverAttachmentUrl.isEmpty() ? "Presente" : "Ausente"));
        
        // Verificar se há uma foto (Base64) para usar multipart
        boolean hasPhoto = driverAttachmentUrl != null && !driverAttachmentUrl.trim().isEmpty() && driverAttachmentUrl.startsWith("data:image/");
        
        if (hasPhoto) {
            android.util.Log.d("MainViewModel", "Usando multipart/form-data para envio com foto");
            finalizeWithMultipart(pickup, observationDriver, occurrenceId, driverAttachmentUrl, "COMPLETED", driverNumberPackages);
        } else {
            android.util.Log.d("MainViewModel", "Usando JSON para envio sem foto");
            finalizeWithJson(pickup, observationDriver, occurrenceId, "COMPLETED", driverNumberPackages);
        }
    }
    
    private void finalizeWithMultipart(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl, String status, Integer driverNumberPackages) {
        try {
            // Criar RequestBody para campos de texto seguindo as melhores práticas
            RequestBody statusBody = RequestBody.create(status, MediaType.parse("text/plain"));
            RequestBody observationBody = RequestBody.create(observationDriver != null ? observationDriver : "", MediaType.parse("text/plain"));
            RequestBody occurrenceIdBody = RequestBody.create(occurrenceId != null ? occurrenceId : "", MediaType.parse("text/plain"));
            
            // Adicionar data e hora de finalização (horário local do Brasil)
            String completionDate = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            RequestBody completionDateBody = RequestBody.create(completionDate, MediaType.parse("text/plain"));
            
            // Adicionar quantidade de itens coletados
            RequestBody driverNumberPackagesBody = null;
            if (driverNumberPackages != null) {
                driverNumberPackagesBody = RequestBody.create(driverNumberPackages.toString(), MediaType.parse("text/plain"));
            }
            
            // Processar a imagem Base64
            MultipartBody.Part imagePart = null;
            android.util.Log.d("MainViewModel", "=== DEBUG DETALHADO DA IMAGEM ===");
            android.util.Log.d("MainViewModel", "driverAttachmentUrl recebido: " + (driverAttachmentUrl != null ? driverAttachmentUrl.substring(0, Math.min(100, driverAttachmentUrl.length())) + "..." : "null"));
            android.util.Log.d("MainViewModel", "Tamanho total da string: " + (driverAttachmentUrl != null ? driverAttachmentUrl.length() : 0));
            
            if (driverAttachmentUrl != null && driverAttachmentUrl.startsWith("data:image/")) {
                // Extrair o Base64 da string data:image/jpeg;base64,xxxxx
                String base64Data = driverAttachmentUrl.substring(driverAttachmentUrl.indexOf(",") + 1);
                android.util.Log.d("MainViewModel", "Base64 extraído (primeiros 100 chars): " + base64Data.substring(0, Math.min(100, base64Data.length())) + "...");
                android.util.Log.d("MainViewModel", "Tamanho do Base64 original: " + base64Data.length());
                
                // Limpar possíveis caracteres de quebra de linha ou espaços
                base64Data = base64Data.replaceAll("\\s+", "");
                android.util.Log.d("MainViewModel", "Tamanho do Base64 após limpeza: " + base64Data.length());
                
                byte[] originalImageBytes = Base64.decode(base64Data, Base64.NO_WRAP);
                android.util.Log.d("MainViewModel", "Tamanho dos bytes decodificados: " + originalImageBytes.length);
                
                // Converter para Bitmap e depois para PNG
                Bitmap bitmap = BitmapFactory.decodeByteArray(originalImageBytes, 0, originalImageBytes.length);
                if (bitmap != null) {
                    android.util.Log.d("MainViewModel", "Bitmap criado com sucesso: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    
                    // Converter bitmap para PNG
                    ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
                    boolean compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOutputStream);
                    byte[] pngImageBytes = pngOutputStream.toByteArray();
                    
                    android.util.Log.d("MainViewModel", "Conversão para PNG: " + (compressed ? "Sucesso" : "Falha"));
                    android.util.Log.d("MainViewModel", "Tamanho original (JPEG): " + originalImageBytes.length + " bytes");
                    android.util.Log.d("MainViewModel", "Tamanho convertido (PNG): " + pngImageBytes.length + " bytes");
                    
                    // Criar MultipartBody.Part com PNG seguindo as melhores práticas
                    // Garantir que o tipo MIME está correto
                    String mimeType = "image/png";
                    RequestBody imageBody = RequestBody.create(pngImageBytes, MediaType.parse(mimeType));
                    // A chave "image" deve corresponder exatamente ao que o backend espera
                    imagePart = MultipartBody.Part.createFormData("image", "driver_photo.png", imageBody);
                    
                    android.util.Log.d("MainViewModel", "✅ MultipartBody.Part criado para campo 'image' - Tamanho: " + pngImageBytes.length + " bytes");
                    android.util.Log.d("MainViewModel", "Content-Type: image/png");
                    android.util.Log.d("MainViewModel", "Filename: driver_photo.png");
                    android.util.Log.d("MainViewModel", "🔄 Enviando como PNG (conversão de JPEG para PNG)");
                    
                    // Liberar bitmap da memória
                    bitmap.recycle();
                } else {
                    android.util.Log.e("MainViewModel", "❌ Falha ao criar bitmap da imagem Base64");
                }
            } else {
                android.util.Log.w("MainViewModel", "❌ Imagem não processada - driverAttachmentUrl inválido ou nulo");
                android.util.Log.w("MainViewModel", "Valor recebido: " + driverAttachmentUrl);
            }
            
            // Log detalhado antes da requisição
            android.util.Log.d("MainViewModel", "=== ENVIANDO REQUISIÇÃO MULTIPART ===");
            android.util.Log.d("MainViewModel", "🎯 Pickup ID: " + pickup.getId());
            android.util.Log.d("MainViewModel", "📊 Status: " + status);
            android.util.Log.d("MainViewModel", "📝 Observation: " + (observationDriver != null ? observationDriver : "null"));
            android.util.Log.d("MainViewModel", "🔢 Occurrence ID: " + (occurrenceId != null ? occurrenceId : "null"));
            android.util.Log.d("MainViewModel", "📷 Image Part: " + (imagePart != null ? "Presente (MultipartBody.Part)" : "null"));
            
            // Log detalhado de cada campo da requisição
            android.util.Log.d("MainViewModel", "=== CAMPOS DA REQUISIÇÃO MULTIPART ===");
            try {
                android.util.Log.d("MainViewModel", "Campo 'status': " + status + " (" + statusBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'observationDriver': " + (observationDriver != null ? observationDriver : "vazio") + " (" + observationBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'occurrenceId': " + (occurrenceId != null ? occurrenceId : "vazio") + " (" + occurrenceIdBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'completionDate': " + completionDate + " (" + completionDateBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'image': " + (imagePart != null ? "ARQUIVO PRESENTE (MultipartBody.Part)" : "❌ NULO - SEM ARQUIVO"));
            } catch (Exception e) {
                android.util.Log.e("MainViewModel", "Erro ao obter tamanhos dos campos: " + e.getMessage());
            }
            
            // Fazer a requisição multipart
            apiService.finalizePickupWithPhoto(pickup.getId(), statusBody, observationBody, occurrenceIdBody, completionDateBody, driverNumberPackagesBody, imagePart)
                    .enqueue(new Callback<Pickup>() {
                        @Override
                        public void onResponse(Call<Pickup> call, Response<Pickup> response) {
                            if (response.isSuccessful()) {
                                Pickup updatedPickup = response.body();
                                android.util.Log.d("MainViewModel", "=== RESPOSTA DA API MULTIPART ===");
                                android.util.Log.d("MainViewModel", "Coleta finalizada com sucesso: " + (updatedPickup != null ? updatedPickup.getId() : "null"));
                                if (updatedPickup != null) {
                                    android.util.Log.d("MainViewModel", "Status: " + updatedPickup.getStatus());
                                    android.util.Log.d("MainViewModel", "ID: " + updatedPickup.getId());
                                }
                                _updateResult.setValue("Coleta finalizada com sucesso!");
                                fetchPickups();
                            } else {
                                android.util.Log.e("MainViewModel", "Falha ao finalizar coleta multipart: " + response.code());
                                try {
                                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "Sem detalhes do erro";
                                    android.util.Log.e("MainViewModel", "Erro detalhado: " + errorBody);
                                } catch (Exception e) {
                                    android.util.Log.e("MainViewModel", "Erro ao ler errorBody: " + e.getMessage());
                                }
                                _updateResult.setValue("Falha ao finalizar a coleta: " + response.code());
                                _isLoading.setValue(false);
                            }
                        }
                        
                        @Override
                        public void onFailure(Call<Pickup> call, Throwable t) {
                            android.util.Log.e("MainViewModel", "Erro de conexão multipart: " + t.getMessage());
                            _updateResult.setValue("Erro de conexão: " + t.getMessage());
                            _isLoading.setValue(false);
                        }
                    });
                    
        } catch (Exception e) {
            android.util.Log.e("MainViewModel", "Erro ao preparar multipart: " + e.getMessage());
            _updateResult.setValue("Erro ao processar imagem: " + e.getMessage());
            _isLoading.setValue(false);
        }
    }
    
    private void finalizeWithJson(Pickup pickup, String observationDriver, String occurrenceId, String status, Integer driverNumberPackages) {
        // Criar Map com todos os dados do motorista
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        
        // Adicionar data e hora de finalização (horário local do Brasil)
        String completionDate = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        updates.put("completionDate", completionDate);
        
        if (observationDriver != null && !observationDriver.trim().isEmpty()) {
            updates.put("observationDriver", observationDriver);
        }
        if (occurrenceId != null && !occurrenceId.trim().isEmpty()) {
            updates.put("occurrenceId", occurrenceId);
        }
        if (driverNumberPackages != null) {
            updates.put("driverNumberPackages", driverNumberPackages);
        }
        
        // Enviar apenas os campos que têm valores
        apiService.finalizePickup(pickup.getId(), updates)
                .enqueue(new Callback<Pickup>() {
                    @Override
                    public void onResponse(Call<Pickup> call, Response<Pickup> response) {
                        if (response.isSuccessful()) {
                            android.util.Log.d("MainViewModel", "Coleta finalizada com sucesso usando JSON");
                            _updateResult.setValue("Coleta finalizada com sucesso!");
                            fetchPickups();
                        } else {
                            android.util.Log.e("MainViewModel", "Falha ao finalizar coleta JSON: " + response.code());
                            _updateResult.setValue("Falha ao finalizar a coleta: " + response.code());
                            _isLoading.setValue(false);
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<Pickup> call, Throwable t) {
                        android.util.Log.e("MainViewModel", "Erro de conexão JSON: " + t.getMessage());
                        _updateResult.setValue("Erro de conexão: " + t.getMessage());
                        _isLoading.setValue(false);
                    }
                });
    }

    public void finalizePickupWithDetailsNotCompleted(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl) {
        _isLoading.setValue(true);
        
        // Logs de debug
        android.util.Log.d("MainViewModel", "=== FINALIZANDO COLETA COMO NÃO COLETADO COM DETALHES ===");
        android.util.Log.d("MainViewModel", "Pickup ID: " + pickup.getId());
        android.util.Log.d("MainViewModel", "Status: NOT_COMPLETED");
        android.util.Log.d("MainViewModel", "Observação: " + observationDriver);
        android.util.Log.d("MainViewModel", "Occurrence ID: " + occurrenceId);
        android.util.Log.d("MainViewModel", "Driver Attachment: " + (driverAttachmentUrl != null && !driverAttachmentUrl.isEmpty() ? "Presente" : "Ausente"));
        
        // Verificar se há uma foto (Base64) para usar multipart
        boolean hasPhoto = driverAttachmentUrl != null && !driverAttachmentUrl.trim().isEmpty() && driverAttachmentUrl.startsWith("data:image/");
        
        if (hasPhoto) {
            android.util.Log.d("MainViewModel", "Usando multipart/form-data para envio com foto (NOT_COMPLETED)");
            finalizeWithMultipart(pickup, observationDriver, occurrenceId, driverAttachmentUrl, "NOT_COMPLETED", null);
        } else {
            android.util.Log.d("MainViewModel", "Usando JSON para envio sem foto (NOT_COMPLETED)");
            finalizeWithJson(pickup, observationDriver, occurrenceId, "NOT_COMPLETED", null);
        }
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