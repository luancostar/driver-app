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
// Removido import java.util.stream.Collectors - não compatível com Android mais antigo
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

// Imports para funcionalidade offline
import com.example.zylogi_motoristas.offline.OfflineRepository;
import com.example.zylogi_motoristas.offline.ConnectivityManager;
import com.example.zylogi_motoristas.offline.SyncManager;

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
    
    // Componentes offline
    private OfflineRepository offlineRepository;
    private ConnectivityManager connectivityManager;
    private SyncManager syncManager;

    public MainViewModel(@NonNull Application application) {
        super(application);
        authSessionManager = new AuthSessionManager(application);
        apiService = RetrofitClient.getClient(application).create(ApiService.class);
        
        // Inicializa componentes offline
        offlineRepository = OfflineRepository.getInstance(application);
        connectivityManager = ConnectivityManager.getInstance(application);
        syncManager = SyncManager.getInstance(application);
        
        // Registra listener para atualizar a tela após sincronização
        setupSyncListener();
    }
    
    private void setupSyncListener() {
        syncManager.addSyncListener(new SyncManager.SyncListener() {
            @Override
            public void onSyncStarted() {
                // Não precisa fazer nada no início da sincronização
            }
            
            @Override
            public void onSyncCompleted(int syncedCount, int failedCount) {
                if (syncedCount > 0) {
                    android.util.Log.i("MainViewModel", "Sincronização concluída com " + syncedCount + " operações. Atualizando tela.");
                    // Atualiza a tela após sincronização bem-sucedida
                    fetchPickups();
                }
            }
            
            @Override
            public void onSyncFailed(String error) {
                android.util.Log.w("MainViewModel", "Sincronização falhou: " + error);
            }
        });
    }

    private void saveOperationOffline(Pickup pickup, String operationType, String observationDriver, String occurrenceId, String driverAttachmentUrl, Integer driverNumberPackages) {
        // Extrair apenas o Base64 da string data:image/jpeg;base64,
        String photoBase64 = null;
        if (driverAttachmentUrl != null && driverAttachmentUrl.startsWith("data:image/")) {
            int commaIndex = driverAttachmentUrl.indexOf(",");
            if (commaIndex != -1 && commaIndex < driverAttachmentUrl.length() - 1) {
                photoBase64 = driverAttachmentUrl.substring(commaIndex + 1);
            }
        }
        
        offlineRepository.saveOperation(
                pickup.getId(),
                operationType,
                observationDriver,
                occurrenceId,
                photoBase64,
                driverNumberPackages,
                new OfflineRepository.OperationSaveCallback() {
                    @Override
                public void onSuccess(long operationId) {
                android.util.Log.i("MainViewModel", "Operação salva offline com sucesso");
                _isLoading.postValue(false);
                _updateResult.postValue("Operação salva offline. Será sincronizada quando houver conexão.");
                    
                    // Atualizar a lista de coletas
                    fetchPickups();
                    
                    // Tentar sincronizar imediatamente
                    syncManager.syncNow();
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.e("MainViewModel", "Erro ao salvar operação offline: " + error);
                    _isLoading.postValue(false);
                    _updateResult.postValue("Erro ao salvar operação offline: " + error);
                }
            }
        );
    }

    public void fetchPickups() {
        try {
            _isLoading.postValue(true);
            String token = authSessionManager.getAuthToken();
            if (token == null) { 
                _error.postValue("Token de autenticação não encontrado");
                _isLoading.postValue(false);
                return; 
            }
            
            JWT jwt = new JWT(token);
            String driverId = jwt.getSubject();
            if (driverId == null) { 
                _error.postValue("ID do motorista não encontrado");
                _isLoading.postValue(false);
                return; 
            }

            String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Tenta buscar da API primeiro
            try {
                apiService.getPickups(driverId, today, today).enqueue(new Callback<List<Pickup>>() {
                    @Override
                    public void onResponse(Call<List<Pickup>> call, Response<List<Pickup>> response) {
                        try {
                            if (response.isSuccessful() && response.body() != null) {
                                List<Pickup> allPickupsFromAPI = response.body();

                                // Filtra por data de agendamento para hoje com tratamento de erro
                                List<Pickup> todayScheduledPickups = new java.util.ArrayList<>();
                                for (Pickup pickup : allPickupsFromAPI) {
                                    try {
                                        if (pickup != null && isScheduledForToday(pickup.getScheduledDate())) {
                                            todayScheduledPickups.add(pickup);
                                        }
                                    } catch (Exception e) {
                                        android.util.Log.w("MainViewModel", "Erro ao filtrar coleta da API: " + e.getMessage());
                                    }
                                }

                                // Salva as coletas no cache para uso offline
                                try {
                                    offlineRepository.cachePickups(todayScheduledPickups, driverId, new OfflineRepository.PickupCacheCallback() {
                                        @Override
                                        public void onSuccess(int count) {
                                            android.util.Log.d("MainViewModel", "Coletas salvas no cache: " + count);
                                        }

                                        @Override
                                        public void onError(String error) {
                                            android.util.Log.e("MainViewModel", "Erro ao salvar no cache: " + error);
                                        }
                                    });
                                } catch (Exception e) {
                                    android.util.Log.w("MainViewModel", "Erro ao salvar cache: " + e.getMessage());
                                }
                                
                                // Carrega e armazena ocorrências no cache quando online
                                loadAndCacheOccurrences();

                                // Filtra para mostrar apenas as pendentes no carrossel
                                List<Pickup> pendingPickups = new java.util.ArrayList<>();
                                for (Pickup pickup : todayScheduledPickups) {
                                    try {
                                        if (pickup != null && "PENDING".equalsIgnoreCase(pickup.getStatus())) {
                                            pendingPickups.add(pickup);
                                        }
                                    } catch (Exception e) {
                                        android.util.Log.w("MainViewModel", "Erro ao filtrar coleta pendente da API: " + e.getMessage());
                                    }
                                }
                                _openPickups.postValue(pendingPickups);

                                // Calcula o progresso com base em TODAS as coletas do dia
                                calculateProgress(todayScheduledPickups);
                            } else {
                                // Se a API falhar, tenta buscar do cache
                                android.util.Log.w("MainViewModel", "Resposta da API não bem-sucedida, tentando cache");
                                loadPickupsFromCache(driverId, today);
                                return; // Não define _isLoading aqui, será definido no loadPickupsFromCache
                            }
                        } catch (Exception e) {
                            android.util.Log.e("MainViewModel", "Erro ao processar resposta da API: " + e.getMessage(), e);
                            loadPickupsFromCache(driverId, today);
                            return; // Não define _isLoading aqui, será definido no loadPickupsFromCache
                        }
                        _isLoading.postValue(false);
                    }

                    @Override
                    public void onFailure(Call<List<Pickup>> call, Throwable t) {
                        android.util.Log.w("MainViewModel", "Falha na API, tentando cache: " + t.getMessage());
                        // Se não conseguir conectar na API, busca do cache
                        loadPickupsFromCache(driverId, today);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("MainViewModel", "Erro ao fazer chamada da API: " + e.getMessage(), e);
                loadPickupsFromCache(driverId, today);
            }
        } catch (Exception e) {
            android.util.Log.e("MainViewModel", "Erro crítico em fetchPickups: " + e.getMessage(), e);
            _error.postValue("Erro crítico ao buscar coletas");
            _openPickups.postValue(new java.util.ArrayList<>());
            calculateProgress(new java.util.ArrayList<>());
            _isLoading.postValue(false);
        }
    }
    
    /**
     * Carrega coletas do cache local quando a API não está disponível
     */
    private void loadPickupsFromCache(String driverId, String date) {
        try {
            offlineRepository.getCachedPickups(driverId, date, new OfflineRepository.PickupListCallback() {
                @Override
                public void onSuccess(List<Pickup> cachedPickups) {
                    try {
                        if (cachedPickups != null && !cachedPickups.isEmpty()) {
                            // Filtra coletas agendadas para hoje com tratamento de erro
                            List<Pickup> todayScheduledPickups = new java.util.ArrayList<>();
                            for (Pickup pickup : cachedPickups) {
                                try {
                                    if (pickup != null && isScheduledForToday(pickup.getScheduledDate())) {
                                        todayScheduledPickups.add(pickup);
                                    }
                                } catch (Exception e) {
                                    android.util.Log.w("MainViewModel", "Erro ao filtrar coleta: " + e.getMessage());
                                }
                            }

                            // Filtra para mostrar apenas as pendentes no carrossel
                            List<Pickup> pendingPickups = new java.util.ArrayList<>();
                            for (Pickup pickup : todayScheduledPickups) {
                                try {
                                    if (pickup != null && "PENDING".equalsIgnoreCase(pickup.getStatus())) {
                                        pendingPickups.add(pickup);
                                    }
                                } catch (Exception e) {
                                    android.util.Log.w("MainViewModel", "Erro ao filtrar coleta pendente: " + e.getMessage());
                                }
                            }
                            
                            // Usar postValue() para threads de background
                            _openPickups.postValue(pendingPickups);
                            calculateProgress(todayScheduledPickups);
                            
                            // Informa que está usando dados offline
                            _error.postValue("Usando dados offline - " + cachedPickups.size() + " coletas carregadas");
                            android.util.Log.i("MainViewModel", "Coletas carregadas do cache: " + cachedPickups.size());
                        } else {
                            _error.postValue("Sem conexão e nenhuma coleta armazenada offline");
                            _openPickups.postValue(new java.util.ArrayList<>());
                            calculateProgress(new java.util.ArrayList<>());
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MainViewModel", "Erro ao processar coletas do cache", e);
                        _error.postValue("Erro ao processar dados offline: " + e.getMessage());
                        _openPickups.postValue(new java.util.ArrayList<>());
                        calculateProgress(new java.util.ArrayList<>());
                    } finally {
                        _isLoading.postValue(false);
                    }
                }

                @Override
                public void onError(String error) {
                    android.util.Log.e("MainViewModel", "Erro ao carregar do cache: " + error);
                    _error.postValue("Erro ao carregar dados offline: " + error);
                    _openPickups.postValue(new java.util.ArrayList<>());
                    calculateProgress(new java.util.ArrayList<>());
                    _isLoading.postValue(false);
                }
            });
        } catch (Exception e) {
            android.util.Log.e("MainViewModel", "Erro crítico ao acessar cache", e);
            _error.postValue("Erro crítico ao acessar dados offline");
            _openPickups.postValue(new java.util.ArrayList<>());
            calculateProgress(new java.util.ArrayList<>());
            _isLoading.postValue(false);
        }
    }

    // O método de finalização continua o mesmo, pois ele chama fetchPickups() no sucesso,
    // o que já dispara a nova filtragem e cálculo de progresso.
    public void finalizePickup(String pickupId, String status) {
        _isLoading.postValue(true);
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
                    _updateResult.postValue("Coleta atualizada com sucesso!");
                    
                    // Atualiza o status no cache local
                    offlineRepository.updateCachedPickupStatus(pickupId, status, new OfflineRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            android.util.Log.d("MainViewModel", "Status da coleta atualizado no cache: " + pickupId);
                        }

                        @Override
                        public void onError(String error) {
                            android.util.Log.e("MainViewModel", "Erro ao atualizar cache: " + error);
                        }
                    });
                    
                    fetchPickups(); // ESSENCIAL: Busca os dados atualizados do servidor
                } else {
                    _updateResult.postValue("Falha ao atualizar a coleta: " + response.code());
                    _isLoading.postValue(false);
                }
            }
            @Override
            public void onFailure(Call<Pickup> call, Throwable t) {
                _updateResult.postValue("Erro de conexão: " + t.getMessage());
                _isLoading.postValue(false);
            }
        });
    }

    // Método para finalização com detalhes do motorista
    public void finalizePickupWithDetails(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl, Integer driverNumberPackages) {
        _isLoading.postValue(true);
        
        // Logs de debug
        android.util.Log.d("MainViewModel", "=== FINALIZANDO COLETA COM DETALHES ===");
        android.util.Log.d("MainViewModel", "Pickup ID: " + pickup.getId());
        android.util.Log.d("MainViewModel", "Status: COMPLETED");
        android.util.Log.d("MainViewModel", "Observação: " + observationDriver);
        android.util.Log.d("MainViewModel", "Occurrence ID: " + occurrenceId);
        android.util.Log.d("MainViewModel", "Driver Attachment: " + (driverAttachmentUrl != null && !driverAttachmentUrl.isEmpty() ? "Presente" : "Ausente"));
        
        // Verifica conectividade
        if (!connectivityManager.isConnected()) {
            android.util.Log.i("MainViewModel", "Sem conectividade - salvando operação offline");
            saveOperationOffline(pickup, "COMPLETED", observationDriver, occurrenceId, driverAttachmentUrl, driverNumberPackages);
            return;
        }
        
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
                    // A chave "driverAttachmentUrl" deve corresponder exatamente ao que o backend espera
                    imagePart = MultipartBody.Part.createFormData("driverAttachmentUrl", "driver_photo.png", imageBody);
                    
                    android.util.Log.d("MainViewModel", "✅ MultipartBody.Part criado para campo 'driverAttachmentUrl' - Tamanho: " + pngImageBytes.length + " bytes");
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
            android.util.Log.d("MainViewModel", "📷 driverAttachmentUrl Part: " + (imagePart != null ? "Presente (MultipartBody.Part)" : "null"));
            
            // Log detalhado de cada campo da requisição
            android.util.Log.d("MainViewModel", "=== CAMPOS DA REQUISIÇÃO MULTIPART ===");
            try {
                android.util.Log.d("MainViewModel", "Campo 'status': " + status + " (" + statusBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'observationDriver': " + (observationDriver != null ? observationDriver : "vazio") + " (" + observationBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'occurrenceId': " + (occurrenceId != null ? occurrenceId : "vazio") + " (" + occurrenceIdBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'completionDate': " + completionDate + " (" + completionDateBody.contentLength() + " bytes)");
                android.util.Log.d("MainViewModel", "Campo 'driverAttachmentUrl': " + (imagePart != null ? "ARQUIVO PRESENTE (MultipartBody.Part)" : "❌ NULO - SEM ARQUIVO"));
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
                                _updateResult.postValue("Coleta finalizada com sucesso!");
                                
                                // Atualiza o status no cache local
                                 offlineRepository.updateCachedPickupStatus(pickup.getId(), status, new OfflineRepository.OperationCallback() {
                                     @Override
                                     public void onSuccess() {
                                         android.util.Log.d("MainViewModel", "Status da coleta atualizado no cache (multipart): " + pickup.getId());
                                     }

                                     @Override
                                     public void onError(String error) {
                                         android.util.Log.e("MainViewModel", "Erro ao atualizar cache (multipart): " + error);
                                     }
                                 });
                                
                                fetchPickups();
                            } else {
                                android.util.Log.e("MainViewModel", "Falha ao finalizar coleta multipart: " + response.code());
                                try {
                                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "Sem detalhes do erro";
                                    android.util.Log.e("MainViewModel", "Erro detalhado: " + errorBody);
                                } catch (Exception e) {
                                    android.util.Log.e("MainViewModel", "Erro ao ler errorBody: " + e.getMessage());
                                }
                                _updateResult.postValue("Falha ao finalizar a coleta: " + response.code());
                                _isLoading.postValue(false);
                            }
                        }
                        
                        @Override
                        public void onFailure(Call<Pickup> call, Throwable t) {
                            android.util.Log.e("MainViewModel", "Erro de conexão multipart: " + t.getMessage());
                            _updateResult.postValue("Erro de conexão: " + t.getMessage());
                            _isLoading.postValue(false);
                        }
                    });
                    
        } catch (Exception e) {
            android.util.Log.e("MainViewModel", "Erro ao preparar multipart: " + e.getMessage());
            _updateResult.postValue("Erro ao processar imagem: " + e.getMessage());
            _isLoading.postValue(false);
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
                            _updateResult.postValue("Coleta finalizada com sucesso!");
                            
                            // Atualiza o status no cache local
                             offlineRepository.updateCachedPickupStatus(pickup.getId(), status, new OfflineRepository.OperationCallback() {
                                 @Override
                                 public void onSuccess() {
                                     android.util.Log.d("MainViewModel", "Status da coleta atualizado no cache (JSON): " + pickup.getId());
                                 }

                                 @Override
                                 public void onError(String error) {
                                     android.util.Log.e("MainViewModel", "Erro ao atualizar cache (JSON): " + error);
                                 }
                             });
                            
                            fetchPickups();
                        } else {
                            android.util.Log.e("MainViewModel", "Falha ao finalizar coleta JSON: " + response.code());
                            _updateResult.postValue("Falha ao finalizar a coleta: " + response.code());
                            _isLoading.postValue(false);
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<Pickup> call, Throwable t) {
                        android.util.Log.e("MainViewModel", "Erro de conexão JSON: " + t.getMessage());
                        _updateResult.postValue("Erro de conexão: " + t.getMessage());
                        _isLoading.postValue(false);
                    }
                });
    }

    public void finalizePickupWithDetailsNotCompleted(Pickup pickup, String observationDriver, String occurrenceId, String driverAttachmentUrl) {
        _isLoading.postValue(true);
        
        // Logs de debug
        android.util.Log.d("MainViewModel", "=== FINALIZANDO COLETA COMO NÃO COLETADO COM DETALHES ===");
        android.util.Log.d("MainViewModel", "Pickup ID: " + pickup.getId());
        android.util.Log.d("MainViewModel", "Status: NOT_COMPLETED");
        android.util.Log.d("MainViewModel", "Observação: " + observationDriver);
        android.util.Log.d("MainViewModel", "Occurrence ID: " + occurrenceId);
        android.util.Log.d("MainViewModel", "Driver Attachment: " + (driverAttachmentUrl != null && !driverAttachmentUrl.isEmpty() ? "Presente" : "Ausente"));
        
        // Para NOT_COMPLETED, driverNumberPackages deve ser 0 (nenhum item coletado)
        Integer driverNumberPackages = 0;
        
        // Verifica conectividade
        if (!connectivityManager.isConnected()) {
            android.util.Log.i("MainViewModel", "Sem conectividade - salvando operação offline (NOT_COMPLETED)");
            saveOperationOffline(pickup, "NOT_COMPLETED", observationDriver, occurrenceId, driverAttachmentUrl, driverNumberPackages);
            return;
        }
        
        // Verificar se há uma foto (Base64) para usar multipart
        boolean hasPhoto = driverAttachmentUrl != null && !driverAttachmentUrl.trim().isEmpty() && driverAttachmentUrl.startsWith("data:image/");
        
        if (hasPhoto) {
            android.util.Log.d("MainViewModel", "Usando multipart/form-data para envio com foto (NOT_COMPLETED)");
            finalizeWithMultipart(pickup, observationDriver, occurrenceId, driverAttachmentUrl, "NOT_COMPLETED", driverNumberPackages);
        } else {
            android.util.Log.d("MainViewModel", "Usando JSON para envio sem foto (NOT_COMPLETED)");
            finalizeWithJson(pickup, observationDriver, occurrenceId, "NOT_COMPLETED", driverNumberPackages);
        }
    }


    // O cálculo de progresso agora considera "COMPLETED" e "NOT_COMPLETED" como finalizadas
    private void calculateProgress(List<Pickup> allPickups) {
        if (allPickups == null || allPickups.isEmpty()) {
            _progressPercentage.postValue(0);
            _progressSummary.postValue("Nenhuma coleta para hoje");
            return;
        }

        int total = allPickups.size();
        // Conta como "concluída" qualquer coleta que não esteja mais pendente
        int concluded = 0;
        for (Pickup pickup : allPickups) {
            if (!"PENDING".equalsIgnoreCase(pickup.getStatus())) {
                concluded++;
            }
        }

        int percentage = (int) (((double) concluded / total) * 100);
        _progressPercentage.postValue(percentage);

        String summary = String.format(Locale.getDefault(), "Coletas concluídas: %d de %d", concluded, total);
        _progressSummary.postValue(summary);
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
    
    /**
     * Carrega e armazena ocorrências no cache para uso offline
     */
    private void loadAndCacheOccurrences() {
        try {
            apiService.getDriverOccurrences().enqueue(new Callback<List<Occurrence>>() {
                @Override
                public void onResponse(Call<List<Occurrence>> call, Response<List<Occurrence>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<Occurrence> occurrences = response.body();
                        offlineRepository.cacheOccurrences(occurrences, new OfflineRepository.OccurrenceCacheCallback() {
                            @Override
                            public void onSuccess(int count) {
                                android.util.Log.d("MainViewModel", "Ocorrências salvas no cache: " + count);
                            }
                            
                            @Override
                            public void onError(String error) {
                                android.util.Log.e("MainViewModel", "Erro ao salvar ocorrências no cache: " + error);
                            }
                        });
                    } else {
                        android.util.Log.w("MainViewModel", "Falha ao carregar ocorrências da API: " + response.code());
                    }
                }
                
                @Override
                public void onFailure(Call<List<Occurrence>> call, Throwable t) {
                    android.util.Log.w("MainViewModel", "Erro ao carregar ocorrências: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            android.util.Log.e("MainViewModel", "Erro ao fazer chamada de ocorrências: " + e.getMessage());
        }
    }
}