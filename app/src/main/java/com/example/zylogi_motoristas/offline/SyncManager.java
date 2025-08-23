package com.example.zylogi_motoristas.offline;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.os.Build;
import androidx.work.*;
import androidx.work.WorkManager;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * Gerenciador de sincronização que coordena WorkManager e conectividade
 * Agenda e executa sincronizações automáticas
 */
public class SyncManager implements ConnectivityManager.ConnectivityListener {
    
    private static final String TAG = "SyncManager";
    private static final String SYNC_WORK_NAME = "offline_sync_work";
    private static final String PERIODIC_SYNC_WORK_NAME = "periodic_sync_work";
    
    private static volatile SyncManager INSTANCE;
    
    private final Context context;
    private final WorkManager workManager;
    private final ConnectivityManager connectivityManager;
    private final OfflineRepository repository;
    
    private boolean isAutoSyncEnabled = true;
    private SyncStatusListener statusListener;
    private List<SyncListener> syncListeners = new ArrayList<>();
    
    // Interface para listeners de sincronização
    public interface SyncListener {
        void onSyncStarted();
        void onSyncCompleted(int syncedCount, int failedCount);
        void onSyncFailed(String error);
    }
    
    private SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(context);
        this.connectivityManager = ConnectivityManager.getInstance(context);
        this.repository = OfflineRepository.getInstance(context);
        
        // Registra listener de conectividade
        connectivityManager.addConnectivityListener(this);
        
        // Registra BroadcastReceiver para ações de sincronização
        setupBroadcastReceiver();
        
        // Agenda sincronização periódica
        schedulePeriodicSync();
        
        Log.i(TAG, "SyncManager inicializado");
        
        // Verificar operações pendentes na inicialização
        getPendingOperationsInfo(new PendingInfoCallback() {
            @Override
            public void onResult(int pendingCount, boolean isConnected) {
                Log.i(TAG, String.format("Inicialização: %d operações pendentes, conectado: %s", 
                    pendingCount, isConnected));
                if (pendingCount > 0 && isConnected) {
                    Log.i(TAG, "Iniciando sincronização automática de " + pendingCount + " operações pendentes");
                    syncNow();
                }
            }
        });
    }
    
    /**
     * Obtém a instância singleton
     */
    public static SyncManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SyncManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SyncManager(context);
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Define listener para status de sincronização
     */
    public void setSyncStatusListener(SyncStatusListener listener) {
        this.statusListener = listener;
    }
    
    /**
     * Habilita/desabilita sincronização automática
     */
    public void setAutoSyncEnabled(boolean enabled) {
        this.isAutoSyncEnabled = enabled;
        Log.i(TAG, "Auto-sync " + (enabled ? "habilitado" : "desabilitado"));
        
        if (enabled && connectivityManager.isConnected()) {
            // Se habilitou e tem conexão, agenda sincronização imediata
            scheduleSyncWork(0);
        }
    }
    
    /**
     * Força uma sincronização manual
     */
    public void forceSyncNow() {
        Log.i(TAG, "Sincronização manual solicitada");
        
        if (!connectivityManager.isConnected()) {
            Log.w(TAG, "Sem conectividade - sincronização manual cancelada");
            notifyStatusListener(SyncStatus.FAILED, "Sem conectividade de rede");
            return;
        }
        
        // Cancela trabalhos pendentes e agenda novo imediatamente
        workManager.cancelUniqueWork(SYNC_WORK_NAME);
        scheduleSyncWork(0);
    }
    
    /**
     * Agenda trabalho de sincronização
     */
    private void scheduleSyncWork(long delayMinutes) {
        if (!isAutoSyncEnabled && delayMinutes > 0) {
            Log.d(TAG, "Auto-sync desabilitado - não agendando sincronização");
            return;
        }
        
        // Configura constraints
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build();
        
        // Cria o trabalho
        OneTimeWorkRequest syncWork = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setConstraints(constraints)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("offline_sync")
            .build();
        
        // Agenda o trabalho
        workManager.enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncWork
        );
        
        // Observa o progresso
        observeWorkProgress(syncWork.getId());
        
        Log.i(TAG, "Sincronização agendada para " + delayMinutes + " minutos");
    }
    
    /**
     * Agenda sincronização periódica
     */
    private void schedulePeriodicSync() {
        // Sincronização a cada 2 horas quando há conectividade
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build();
        
        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
            SyncWorker.class, 2, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag("periodic_sync")
            .build();
        
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        );
        
        Log.i(TAG, "Sincronização periódica agendada (2 horas)");
    }
    
    /**
     * Observa o progresso do trabalho de sincronização
     */
    private void observeWorkProgress(java.util.UUID workId) {
        workManager.getWorkInfoByIdLiveData(workId).observeForever(workInfo -> {
            if (workInfo != null) {
                WorkInfo.State state = workInfo.getState();
                
                switch (state) {
                    case RUNNING:
                        Log.d(TAG, "Sincronização em execução");
                        notifyStatusListener(SyncStatus.RUNNING, null);
                        break;
                        
                    case SUCCEEDED:
                        Data outputData = workInfo.getOutputData();
                        int total = outputData.getInt("total_operations", 0);
                        int successful = outputData.getInt("successful_operations", 0);
                        int failed = outputData.getInt("failed_operations", 0);
                        
                        String message = String.format("Sincronizadas %d de %d operações", successful, total);
                        Log.i(TAG, "Sincronização concluída: " + message);
                        notifyStatusListener(SyncStatus.SUCCESS, message);
                        
                        // Notifica os SyncListeners sobre a conclusão
                        notifySyncCompleted(successful, failed);
                        break;
                        
                    case FAILED:
                        Log.w(TAG, "Sincronização falhou");
                        notifyStatusListener(SyncStatus.FAILED, "Falha na sincronização");
                        
                        // Notifica os SyncListeners sobre a falha
                        notifySyncFailed("Falha na sincronização");
                        break;
                        
                    case CANCELLED:
                        Log.i(TAG, "Sincronização cancelada");
                        notifyStatusListener(SyncStatus.CANCELLED, "Sincronização cancelada");
                        break;
                        
                    case ENQUEUED:
                        Log.d(TAG, "Sincronização na fila");
                        notifyStatusListener(SyncStatus.QUEUED, null);
                        break;
                        
                    case BLOCKED:
                        Log.d(TAG, "Sincronização bloqueada (aguardando constraints)");
                        notifyStatusListener(SyncStatus.WAITING, "Aguardando conectividade");
                        break;
                }
            }
        });
    }
    
    /**
     * Notifica listener sobre mudanças de status
     */
    private void notifyStatusListener(SyncStatus status, String message) {
        if (statusListener != null) {
            try {
                statusListener.onSyncStatusChanged(status, message);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao notificar status listener", e);
            }
        }
    }
    
    /**
     * Obtém informações sobre operações pendentes
     */
    public void getPendingOperationsInfo(PendingInfoCallback callback) {
        repository.getPendingOperationsCount(new OfflineRepository.CountCallback() {
            @Override
            public void onCount(int count) {
                if (callback != null) {
                    callback.onResult(count, connectivityManager.isConnected());
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Erro ao obter info de operações pendentes: " + error);
                if (callback != null) {
                    callback.onResult(0, connectivityManager.isConnected());
                }
            }
        });
    }
    
    /**
     * Adiciona um listener de sincronização
     */
    public void addSyncListener(SyncListener listener) {
        if (listener != null && !syncListeners.contains(listener)) {
            syncListeners.add(listener);
            Log.d(TAG, "Listener de sincronização adicionado");
        }
    }
    
    /**
     * Remove um listener de sincronização
     */
    public void removeSyncListener(SyncListener listener) {
        if (listener != null) {
            syncListeners.remove(listener);
            Log.d(TAG, "Listener de sincronização removido");
        }
    }
    
    /**
     * Notifica listeners sobre início da sincronização
     */
    private void notifySyncStarted() {
        for (SyncListener listener : syncListeners) {
            try {
                listener.onSyncStarted();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao notificar início de sync: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notifica listeners sobre conclusão da sincronização
     */
    private void notifySyncCompleted(int syncedCount, int failedCount) {
        Log.i(TAG, "notifySyncCompleted chamado - syncedCount: " + syncedCount + ", failedCount: " + failedCount + ", listeners: " + syncListeners.size());
        for (SyncListener listener : syncListeners) {
            try {
                Log.d(TAG, "Notificando listener: " + listener.getClass().getSimpleName());
                listener.onSyncCompleted(syncedCount, failedCount);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao notificar conclusão de sync: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notifica listeners sobre falha na sincronização
     */
    private void notifySyncFailed(String error) {
        for (SyncListener listener : syncListeners) {
            try {
                listener.onSyncFailed(error);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao notificar falha de sync: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onConnectivityChanged(boolean connected) {
        Log.i(TAG, "Conectividade mudou: " + connected);
        if (connected && isAutoSyncEnabled) {
            // Conectividade restaurada - reseta operações falhadas e verifica pendentes
            repository.resetFailedOperations(new OfflineRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Operações falhadas resetadas - verificando operações pendentes");
                    
                    repository.getPendingOperationsCount(new OfflineRepository.CountCallback() {
                        @Override
                        public void onCount(int count) {
                            if (count > 0) {
                                Log.i(TAG, "Conectividade restaurada - iniciando sincronização de " + count + " operações pendentes");
                                syncNow();
                            } else {
                                Log.d(TAG, "Conectividade restaurada - nenhuma operação pendente para sincronizar");
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Erro ao verificar operações pendentes: " + error);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Erro ao resetar operações falhadas: " + error);
                    // Mesmo com erro no reset, tenta sincronizar
                    repository.getPendingOperationsCount(new OfflineRepository.CountCallback() {
                        @Override
                        public void onCount(int count) {
                            if (count > 0) {
                                Log.i(TAG, "Conectividade restaurada - iniciando sincronização de " + count + " operações pendentes");
                                syncNow();
                            } else {
                                Log.d(TAG, "Conectividade restaurada - nenhuma operação pendente para sincronizar");
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Erro ao verificar operações pendentes: " + error);
                        }
                    });
                }
            });
        }
    }
    
    /**
     * Inicia sincronização imediata
     */
    public void syncNow() {
        if (!connectivityManager.isConnected()) {
            Log.w(TAG, "Tentativa de sincronização sem conectividade - cancelada");
            return;
        }
        
        Log.i(TAG, "Iniciando sincronização imediata");
        
        // Inicia sincronização imediata usando WorkManager
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        
        WorkManager.getInstance(context).enqueue(syncRequest);
        
        // Observa o progresso do trabalho
        observeWorkProgress(syncRequest.getId());
        
        notifySyncStarted();
    }
    
    /**
     * Cancela todas as sincronizações pendentes
     */
    public void cancelAllSync() {
        workManager.cancelUniqueWork(SYNC_WORK_NAME);
        workManager.cancelAllWorkByTag("offline_sync");
        Log.i(TAG, "Todas as sincronizações canceladas");
    }
    
    /**
     * Deleta uma operação específica por ID
     */
    public void deleteOperationById(int operationId) {
        Log.i(TAG, "Deletando operação " + operationId + " via método público");
        repository.deleteOperationById(operationId, new OfflineRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Operação " + operationId + " deletada com sucesso");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Erro ao deletar operação " + operationId + ": " + error);
            }
        });
    }
    
    /**
     * Limpa recursos
     */
    public void cleanup() {
        connectivityManager.removeConnectivityListener(this);
        cancelAllSync();
        Log.i(TAG, "SyncManager limpo");
    }
    
    // Implementação do ConnectivityListener
    
    // Enums e interfaces
    public enum SyncStatus {
        CONNECTED,
        DISCONNECTED,
        QUEUED,
        WAITING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED
    }
    
    public interface SyncStatusListener {
        void onSyncStatusChanged(SyncStatus status, String message);
    }
    
    /**
     * Configura o BroadcastReceiver para ações de sincronização
     */
    private void setupBroadcastReceiver() {
        BroadcastReceiver syncReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "Broadcast recebido: " + action);
                
                if ("com.example.zylogi_motoristas.FORCE_SYNC".equals(action)) {
                    Log.i(TAG, "Forçando sincronização via broadcast");
                    syncNow();
                } else if ("com.example.zylogi_motoristas.RESET_OPERATION".equals(action)) {
                    int operationId = intent.getIntExtra("operation_id", -1);
                    if (operationId != -1) {
                        Log.i(TAG, "Resetando operação " + operationId + " via broadcast");
                        repository.resetOperationRetryCount(operationId, new OfflineRepository.OperationCallback() {
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "Operação " + operationId + " resetada com sucesso");
                                syncNow();
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Erro ao resetar operação " + operationId + ": " + error);
                            }
                        });
                    }
                } else if ("com.example.zylogi_motoristas.UPDATE_OCCURRENCE_ID".equals(action)) {
                    int operationId = intent.getIntExtra("operation_id", -1);
                    String occurrenceId = intent.getStringExtra("occurrence_id");
                    if (operationId != -1) {
                        Log.i(TAG, "Atualizando occurrenceId da operação " + operationId + " para: " + occurrenceId);
                        repository.updateOperationOccurrenceId(operationId, occurrenceId, new OfflineRepository.OperationCallback() {
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "OccurrenceId da operação " + operationId + " atualizado com sucesso");
                                syncNow();
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Erro ao atualizar occurrenceId da operação " + operationId + ": " + error);
                            }
                        });
                    }
                } else if ("com.example.zylogi_motoristas.DELETE_OPERATION".equals(action)) {
                    int operationId = intent.getIntExtra("operation_id", -1);
                    if (operationId != -1) {
                        Log.i(TAG, "Deletando operação " + operationId + " via broadcast");
                        repository.deleteOperationById(operationId, new OfflineRepository.OperationCallback() {
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "Operação " + operationId + " deletada com sucesso");
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Erro ao deletar operação " + operationId + ": " + error);
                            }
                        });
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.zylogi_motoristas.FORCE_SYNC");
        filter.addAction("com.example.zylogi_motoristas.RESET_OPERATION");
        filter.addAction("com.example.zylogi_motoristas.UPDATE_OCCURRENCE_ID");
        filter.addAction("com.example.zylogi_motoristas.DELETE_OPERATION");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(syncReceiver, filter);
        }
        Log.d(TAG, "BroadcastReceiver registrado para ações de sincronização");
    }
    
    public interface PendingInfoCallback {
        void onResult(int pendingCount, boolean isConnected);
    }
}