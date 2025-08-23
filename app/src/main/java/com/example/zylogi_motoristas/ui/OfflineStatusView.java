package com.example.zylogi_motoristas.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.example.zylogi_motoristas.R;
import com.example.zylogi_motoristas.offline.ConnectivityManager;
import com.example.zylogi_motoristas.offline.OfflineRepository;
import com.example.zylogi_motoristas.offline.SyncManager;

/**
 * Componente de UI que mostra o status das operações offline
 */
public class OfflineStatusView extends LinearLayout {
    private static final String TAG = "OfflineStatusView";
    
    private TextView statusText;
    private TextView pendingCountText;
    private View statusIndicator;
    private View syncButton;
    
    private ConnectivityManager connectivityManager;
    private OfflineRepository offlineRepository;
    private SyncManager syncManager;
    
    private boolean isConnected = true;
    private int pendingOperations = 0;
    
    public OfflineStatusView(Context context) {
        super(context);
        init(context);
    }
    
    public OfflineStatusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public OfflineStatusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.view_offline_status, this, true);
        
        statusText = findViewById(R.id.tv_status);
        pendingCountText = findViewById(R.id.tv_pending_count);
        statusIndicator = findViewById(R.id.view_status_indicator);
        syncButton = findViewById(R.id.btn_sync);
        
        // Inicializar managers
        connectivityManager = ConnectivityManager.getInstance(context);
        offlineRepository = OfflineRepository.getInstance(context);
        syncManager = SyncManager.getInstance(context);
        
        // Configurar listeners
        setupListeners();
        
        // Configurar click do botão de sincronização
        syncButton.setOnClickListener(v -> {
            if (isConnected && pendingOperations > 0) {
                syncManager.syncNow();
                updateSyncButtonState(true);
            }
        });
        
        // Estado inicial
        updateUI();
    }
    
    private void setupListeners() {
        // Listener de conectividade
        connectivityManager.addConnectivityListener(new ConnectivityManager.ConnectivityListener() {
            @Override
            public void onConnectivityChanged(boolean connected) {
                isConnected = connected;
                post(() -> updateUI());
            }
        });
        
        // Listener de sincronização
        syncManager.addSyncListener(new SyncManager.SyncListener() {
            @Override
            public void onSyncStarted() {
                post(() -> updateSyncButtonState(true));
            }
            
            @Override
            public void onSyncCompleted(int syncedCount, int failedCount) {
                post(() -> {
                    updateSyncButtonState(false);
                    updatePendingCount();
                });
            }
            
            @Override
            public void onSyncFailed(String error) {
                post(() -> updateSyncButtonState(false));
            }
        });
    }
    
    private void updateUI() {
        updateConnectivityStatus();
        updatePendingCount();
        updateSyncButtonState(false);
    }
    
    private void updateConnectivityStatus() {
        if (isConnected) {
            statusText.setText("Online");
            statusIndicator.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            statusText.setText("Offline");
            statusIndicator.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
        }
    }
    
    private void updatePendingCount() {
        offlineRepository.getPendingOperationsCount(new OfflineRepository.CountCallback() {
            @Override
            public void onCount(int count) {
                pendingOperations = count;
                post(() -> {
                    if (count > 0) {
                        pendingCountText.setVisibility(VISIBLE);
                        pendingCountText.setText(String.format("%d pendente%s", count, count > 1 ? "s" : ""));
                        syncButton.setVisibility(isConnected ? VISIBLE : GONE);
                    } else {
                        pendingCountText.setVisibility(GONE);
                        syncButton.setVisibility(GONE);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e(TAG, "Erro ao obter contagem de operações pendentes: " + error);
            }
        });
    }
    
    private void updateSyncButtonState(boolean syncing) {
        if (syncButton instanceof TextView) {
            TextView syncTextButton = (TextView) syncButton;
            syncTextButton.setText(syncing ? "Sincronizando..." : "Sincronizar");
            syncTextButton.setEnabled(!syncing);
        }
    }
    
    /**
     * Atualiza o status manualmente (útil para refresh)
     */
    public void refresh() {
        updateUI();
    }
    
    /**
     * Limpa listeners quando a view é destruída
     */
    public void cleanup() {
        // TODO: Implementar remoção de listeners se necessário
    }
}