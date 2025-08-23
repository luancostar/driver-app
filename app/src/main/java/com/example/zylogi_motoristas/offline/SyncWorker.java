package com.example.zylogi_motoristas.offline;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;

import com.example.zylogi_motoristas.ApiService;
import com.example.zylogi_motoristas.RetrofitClient;
import com.example.zylogi_motoristas.AuthSessionManager;
import com.example.zylogi_motoristas.Pickup;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Worker para sincronização de operações pendentes em background
 * Executa quando há conectividade disponível
 */
public class SyncWorker extends Worker {
    
    private static final String TAG = "SyncWorker";
    private static final int MAX_SYNC_ATTEMPTS = 3;
    private static final int SYNC_TIMEOUT_MINUTES = 10;
    
    private OfflineRepository repository;
    private ApiService apiService;
    private AuthSessionManager authManager;
    
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        repository = OfflineRepository.getInstance(context);
        apiService = RetrofitClient.getClient(context).create(ApiService.class);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Iniciando sincronização de operações pendentes");
        
        try {
            // Verifica conectividade antes de iniciar
            ConnectivityManager connectivityManager = ConnectivityManager.getInstance(getApplicationContext());
            if (!connectivityManager.isConnected()) {
                Log.w(TAG, "Sem conectividade - cancelando sincronização");
                return Result.retry();
            }
            
            // Obtém operações pendentes
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean success = new AtomicBoolean(false);
            AtomicInteger totalOperations = new AtomicInteger(0);
            AtomicInteger successfulOperations = new AtomicInteger(0);
            AtomicInteger failedOperations = new AtomicInteger(0);
            
            repository.getRetryableOperations(new OfflineRepository.OperationListCallback() {
                @Override
                public void onSuccess(List<PendingOperation> operations) {
                    totalOperations.set(operations.size());
                    
                    if (operations.isEmpty()) {
                        Log.i(TAG, "Nenhuma operação pendente para sincronizar");
                        success.set(true);
                        latch.countDown();
                        return;
                    }
                    
                    Log.i(TAG, "Sincronizando " + operations.size() + " operações pendentes");
                    
                    // Sincroniza cada operação
                    CountDownLatch operationsLatch = new CountDownLatch(operations.size());
                    
                    for (PendingOperation operation : operations) {
                        syncOperation(operation, new SyncCallback() {
                            @Override
                            public void onSuccess() {
                                successfulOperations.incrementAndGet();
                                operationsLatch.countDown();
                            }
                            
                            @Override
                            public void onFailure(String error) {
                                failedOperations.incrementAndGet();
                                operationsLatch.countDown();
                            }
                        });
                    }
                    
                    // Aguarda todas as operações terminarem
                    new Thread(() -> {
                        try {
                            operationsLatch.await(SYNC_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                            
                            // Considera sucesso se pelo menos 50% das operações foram sincronizadas
                            boolean syncSuccess = successfulOperations.get() >= (totalOperations.get() / 2);
                            success.set(syncSuccess);
                            
                            Log.i(TAG, String.format("Sincronização concluída - Total: %d, Sucesso: %d, Falha: %d",
                                totalOperations.get(), successfulOperations.get(), failedOperations.get()));
                            
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Timeout na sincronização", e);
                            success.set(false);
                        } finally {
                            latch.countDown();
                        }
                    }).start();
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Erro ao obter operações pendentes: " + error);
                    success.set(false);
                    latch.countDown();
                }
            });
            
            // Aguarda a sincronização terminar
            latch.await(SYNC_TIMEOUT_MINUTES + 1, TimeUnit.MINUTES);
            
            if (success.get()) {
                Log.i(TAG, "Sincronização concluída com sucesso");
                
                // Retorna dados sobre a sincronização
                Data outputData = new Data.Builder()
                    .putInt("total_operations", totalOperations.get())
                    .putInt("successful_operations", successfulOperations.get())
                    .putInt("failed_operations", failedOperations.get())
                    .build();
                
                return Result.success(outputData);
            } else {
                Log.w(TAG, "Sincronização falhou - tentando novamente mais tarde");
                return Result.retry();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante sincronização", e);
            return Result.failure();
        }
    }
    
    /**
     * Sincroniza uma operação específica
     */
    private void syncOperation(PendingOperation operation, SyncCallback callback) {
        try {
            Log.d(TAG, "Sincronizando operação: " + operation.toString());
            
            // Prepara os dados para envio
            if (operation.getDriverAttachmentBase64() != null && 
                !operation.getDriverAttachmentBase64().isEmpty()) {
                // Operação com foto - usa multipart
                syncOperationWithPhoto(operation, callback);
            } else {
                // Operação sem foto - usa JSON
                syncOperationWithoutPhoto(operation, callback);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erro ao sincronizar operação " + operation.getId(), e);
            markOperationAsFailed(operation, e.getMessage());
            callback.onFailure(e.getMessage());
        }
    }
    
    /**
     * Sincroniza operação com foto usando multipart
     */
    private void syncOperationWithPhoto(PendingOperation operation, SyncCallback callback) {
        try {
            // Converte Base64 para bytes
            String base64Data = operation.getDriverAttachmentBase64();
            if (base64Data.startsWith("data:image/")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }
            
            byte[] imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP);
            
            // Cria o MultipartBody.Part
            RequestBody imageBody = RequestBody.create(MediaType.parse("image/jpeg"), imageBytes);
            MultipartBody.Part imagePart = MultipartBody.Part.createFormData(
                "driverAttachmentUrl", "image.jpg", imageBody);
            
            // Prepara outros parâmetros
            RequestBody pickupIdBody = RequestBody.create(MediaType.parse("text/plain"), operation.getPickupId());
            RequestBody statusBody = RequestBody.create(MediaType.parse("text/plain"), operation.getOperationType());
            RequestBody observationBody = operation.getObservationDriver() != null ? 
                RequestBody.create(MediaType.parse("text/plain"), operation.getObservationDriver()) : null;
            
            // OccurrenceId: só envia se não estiver vazio e for um UUID válido
            RequestBody occurrenceIdBody = null;
            if (operation.getOccurrenceId() != null && !operation.getOccurrenceId().trim().isEmpty()) {
                occurrenceIdBody = RequestBody.create(MediaType.parse("text/plain"), operation.getOccurrenceId());
            }
            
            RequestBody packagesBody = operation.getDriverNumberPackages() != null ? 
                RequestBody.create(MediaType.parse("text/plain"), operation.getDriverNumberPackages().toString()) : null;
            
            // CompletionDate: converte timestamp para ISO 8601
            RequestBody completionDateBody = null;
            if (operation.getCompletionDate() != null) {
                try {
                    long timestamp = Long.parseLong(operation.getCompletionDate());
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    String isoDate = sdf.format(new java.util.Date(timestamp));
                    completionDateBody = RequestBody.create(MediaType.parse("text/plain"), isoDate);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Formato de data inválido: " + operation.getCompletionDate());
                    // Se já estiver em formato ISO, usa como está
                    completionDateBody = RequestBody.create(MediaType.parse("text/plain"), operation.getCompletionDate());
                }
            }
            
            // Faz a chamada da API
            Call<Pickup> call = apiService.finalizePickupWithPhoto(
                operation.getPickupId(), statusBody, observationBody, occurrenceIdBody, 
                completionDateBody, packagesBody, imagePart);
            
            call.enqueue(new Callback<Pickup>() {
                @Override
                public void onResponse(Call<Pickup> call, Response<Pickup> response) {
                    if (response.isSuccessful()) {
                        Log.i(TAG, "Operação com foto sincronizada com sucesso: " + operation.getId());
                        removeOperation(operation);
                        callback.onSuccess();
                    } else {
                        String errorBody = "";
                        try {
                            if (response.errorBody() != null) {
                                errorBody = response.errorBody().string();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Erro ao ler corpo da resposta de erro", e);
                        }
                        
                        String error = String.format("Erro HTTP: %d: %s. Corpo: %s", 
                            response.code(), response.message(), errorBody);
                        Log.w(TAG, "Falha na sincronização com foto: " + error);
                        
                        // Log detalhado dos dados enviados para debug
                        Log.d(TAG, String.format("Dados enviados - PickupId: %s, Status: %s, Observação: %s, OccurrenceId: %s, Packages: %s, CompletionDate: %s",
                            operation.getPickupId(), operation.getOperationType(), 
                            operation.getObservationDriver(), operation.getOccurrenceId(),
                            operation.getDriverNumberPackages(), operation.getCompletionDate()));
                        
                        handleSyncFailure(operation, error);
                        callback.onFailure(error);
                    }
                }
                
                @Override
                public void onFailure(Call<Pickup> call, Throwable t) {
                    String error = "Erro de rede: " + t.getMessage();
                    Log.w(TAG, "Falha na sincronização com foto: " + error);
                    handleSyncFailure(operation, error);
                    callback.onFailure(error);
                }
            });
            
        } catch (Exception e) {
            String error = "Erro ao processar foto: " + e.getMessage();
            Log.e(TAG, error, e);
            markOperationAsFailed(operation, error);
            callback.onFailure(error);
        }
    }
    
    /**
     * Sincroniza operação sem foto usando JSON
     */
    private void syncOperationWithoutPhoto(PendingOperation operation, SyncCallback callback) {
        try {
            // Criar Map com todos os dados da operação
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("status", operation.getOperationType());
            
            // Adicionar data de finalização se disponível (convertendo para ISO 8601)
            if (operation.getCompletionDate() != null) {
                try {
                    long timestamp = Long.parseLong(operation.getCompletionDate());
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    String isoDate = sdf.format(new java.util.Date(timestamp));
                    updates.put("completionDate", isoDate);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Formato de data inválido: " + operation.getCompletionDate());
                    // Se já estiver em formato ISO, usa como está
                    updates.put("completionDate", operation.getCompletionDate());
                }
            }
            
            // Adicionar observação do motorista se disponível
            if (operation.getObservationDriver() != null && !operation.getObservationDriver().trim().isEmpty()) {
                updates.put("observationDriver", operation.getObservationDriver());
            }
            
            // Adicionar ID da ocorrência se disponível (só se não estiver vazio)
            if (operation.getOccurrenceId() != null && !operation.getOccurrenceId().trim().isEmpty()) {
                updates.put("occurrenceId", operation.getOccurrenceId());
            }
            
            // Adicionar número de pacotes se disponível
            if (operation.getDriverNumberPackages() != null) {
                updates.put("driverNumberPackages", operation.getDriverNumberPackages());
            }
            
            Log.d(TAG, "Sincronizando operação sem foto: " + operation.getPickupId() + " com status: " + operation.getOperationType());
            
            // Fazer chamada da API
            retrofit2.Call<com.example.zylogi_motoristas.Pickup> call = apiService.finalizePickup(operation.getPickupId(), updates);
            
            call.enqueue(new retrofit2.Callback<com.example.zylogi_motoristas.Pickup>() {
                @Override
                public void onResponse(retrofit2.Call<com.example.zylogi_motoristas.Pickup> call, retrofit2.Response<com.example.zylogi_motoristas.Pickup> response) {
                    if (response.isSuccessful()) {
                        Log.i(TAG, "Operação sem foto sincronizada com sucesso: " + operation.getId());
                        removeOperation(operation);
                        callback.onSuccess();
                    } else {
                        String errorBody = "";
                        try {
                            if (response.errorBody() != null) {
                                errorBody = response.errorBody().string();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Erro ao ler corpo da resposta de erro", e);
                        }
                        
                        String error = String.format("Erro HTTP: %d: %s. Corpo: %s", 
                            response.code(), response.message(), errorBody);
                        Log.w(TAG, "Falha na sincronização sem foto: " + error);
                        
                        // Log detalhado dos dados enviados para debug
                        Log.d(TAG, String.format("Dados enviados - PickupId: %s, Updates: %s",
                            operation.getPickupId(), updates.toString()));
                        
                        handleSyncFailure(operation, error);
                        callback.onFailure(error);
                    }
                }
                
                @Override
                public void onFailure(retrofit2.Call<com.example.zylogi_motoristas.Pickup> call, Throwable t) {
                    String error = "Erro de rede: " + t.getMessage();
                    Log.w(TAG, "Falha na sincronização sem foto: " + error);
                    handleSyncFailure(operation, error);
                    callback.onFailure(error);
                }
            });
            
        } catch (Exception e) {
            String error = "Erro ao processar operação sem foto: " + e.getMessage();
            Log.e(TAG, error, e);
            markOperationAsFailed(operation, error);
            callback.onFailure(error);
        }
    }
    
    /**
     * Remove operação após sincronização bem-sucedida
     */
    private void removeOperation(PendingOperation operation) {
        repository.removeOperation(operation.getId(), new OfflineRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Operação removida do banco local: " + operation.getId());
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Erro ao remover operação do banco: " + error);
            }
        });
    }
    
    /**
     * Marca operação como falhada
     */
    private void markOperationAsFailed(PendingOperation operation, String error) {
        repository.markOperationAsFailed(operation.getId(), error, new OfflineRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Operação marcada como falhada: " + operation.getId());
            }
            
            @Override
            public void onError(String err) {
                Log.e(TAG, "Erro ao marcar operação como falhada: " + err);
            }
        });
    }
    
    /**
     * Trata falhas de sincronização com estratégia de retry
     */
    private void handleSyncFailure(PendingOperation operation, String error) {
        int currentAttempts = operation.getRetryCount() + 1;
        
        if (currentAttempts < MAX_SYNC_ATTEMPTS) {
            Log.w(TAG, String.format("Falha na sincronização (tentativa %d/%d): %s", 
                currentAttempts, MAX_SYNC_ATTEMPTS, error));
            
            // Incrementa contador de tentativas
            repository.incrementRetryCount(operation.getId(), new OfflineRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Contador de retry incrementado para operação: " + operation.getId());
                }
                
                @Override
                public void onError(String err) {
                    Log.e(TAG, "Erro ao incrementar contador de retry: " + err);
                }
            });
        } else {
            Log.e(TAG, String.format("Operação falhou após %d tentativas: %s", 
                MAX_SYNC_ATTEMPTS, error));
            markOperationAsFailed(operation, error);
        }
    }
    
    // Interface para callback de sincronização
    private interface SyncCallback {
        void onSuccess();
        void onFailure(String error);
    }
}