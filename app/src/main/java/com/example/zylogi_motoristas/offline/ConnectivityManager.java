package com.example.zylogi_motoristas.offline;

import android.content.Context;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador de conectividade de rede
 * Monitora o status da conexão e notifica quando a rede fica disponível
 */
public class ConnectivityManager {
    
    private static final String TAG = "ConnectivityManager";
    private static volatile ConnectivityManager INSTANCE;
    
    private final android.net.ConnectivityManager systemConnectivityManager;
    private final List<ConnectivityListener> listeners;
    private boolean isConnected = false;
    private NetworkCallback networkCallback;
    
    // Interface para listeners de conectividade
    public interface ConnectivityListener {
        void onConnectivityChanged(boolean connected);
    }
    
    private ConnectivityManager(Context context) {
        systemConnectivityManager = (android.net.ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        listeners = new ArrayList<>();
        
        // Verifica o status inicial
        checkInitialConnectivity();
        
        // Registra o callback de rede
        registerNetworkCallback();
    }
    
    /**
     * Obtém a instância singleton do gerenciador
     */
    public static ConnectivityManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ConnectivityManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ConnectivityManager(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Verifica o status inicial da conectividade
     */
    private void checkInitialConnectivity() {
        try {
            Network activeNetwork = systemConnectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities capabilities = systemConnectivityManager
                    .getNetworkCapabilities(activeNetwork);
                
                if (capabilities != null) {
                    isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                 capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                }
            }
            
            Log.i(TAG, "Status inicial de conectividade: " + (isConnected ? "Conectado" : "Desconectado"));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar conectividade inicial", e);
            isConnected = false;
        }
    }
    
    /**
     * Registra o callback para monitorar mudanças de rede
     */
    private void registerNetworkCallback() {
        try {
            NetworkRequest.Builder builder = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            
            networkCallback = new NetworkCallback();
            systemConnectivityManager.registerNetworkCallback(builder.build(), networkCallback);
            
            Log.i(TAG, "Callback de rede registrado");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar callback de rede", e);
        }
    }
    
    /**
     * Adiciona um listener para mudanças de conectividade
     */
    public void addConnectivityListener(ConnectivityListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
                Log.d(TAG, "Listener adicionado. Total: " + listeners.size());
            }
        }
    }
    
    /**
     * Remove um listener
     */
    public void removeConnectivityListener(ConnectivityListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            Log.d(TAG, "Listener removido. Total: " + listeners.size());
        }
    }
    
    /**
     * Verifica se há conexão com a internet
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Força uma verificação de conectividade
     */
    public void checkConnectivity() {
        checkInitialConnectivity();
        notifyListeners(isConnected);
    }
    
    /**
     * Notifica todos os listeners sobre mudanças de conectividade
     */
    private void notifyListeners(boolean connected) {
        synchronized (listeners) {
            for (ConnectivityListener listener : listeners) {
                try {
                    listener.onConnectivityChanged(connected);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao notificar listener", e);
                }
            }
        }
    }
    
    /**
     * Testa a conectividade fazendo uma requisição real
     */
    public void testConnectivity(ConnectivityTestCallback callback) {
        new Thread(() -> {
            try {
                // Tenta fazer uma requisição HTTP simples
                java.net.URL url = new java.net.URL("https://www.google.com");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                boolean success = responseCode == 200;
                
                Log.d(TAG, "Teste de conectividade: " + (success ? "Sucesso" : "Falha") + 
                    " (código: " + responseCode + ")");
                
                if (callback != null) {
                    callback.onTestResult(success);
                }
                
            } catch (Exception e) {
                Log.w(TAG, "Teste de conectividade falhou", e);
                if (callback != null) {
                    callback.onTestResult(false);
                }
            }
        }).start();
    }
    
    /**
     * Limpa recursos quando não precisar mais do gerenciador
     */
    public void cleanup() {
        try {
            if (networkCallback != null) {
                systemConnectivityManager.unregisterNetworkCallback(networkCallback);
                Log.i(TAG, "Callback de rede removido");
            }
            
            synchronized (listeners) {
                listeners.clear();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro na limpeza do ConnectivityManager", e);
        }
    }
    
    /**
     * Callback interno para monitorar mudanças de rede
     */
    private class NetworkCallback extends android.net.ConnectivityManager.NetworkCallback {
        
        @Override
        public void onAvailable(@NonNull Network network) {
            Log.i(TAG, "Rede disponível: " + network);
            
            // Verifica se realmente tem internet
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Aguarda um pouco para estabilizar
                    
                    NetworkCapabilities capabilities = systemConnectivityManager
                        .getNetworkCapabilities(network);
                    
                    if (capabilities != null && 
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        
                        boolean wasConnected = isConnected;
                        isConnected = true;
                        
                        if (!wasConnected) {
                             Log.i(TAG, "Conectividade restaurada");
                             ConnectivityManager.this.notifyListeners(true);
                         }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar rede disponível", e);
                }
            }).start();
        }
        
        @Override
        public void onLost(@NonNull Network network) {
            Log.i(TAG, "Rede perdida: " + network);
            
            boolean wasConnected = isConnected;
            isConnected = false;
            
            if (wasConnected) {
                 Log.i(TAG, "Conectividade perdida");
                 ConnectivityManager.this.notifyListeners(false);
             }
        }
        
        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
            boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            
            boolean wasConnected = isConnected;
            isConnected = hasInternet;
            
            if (wasConnected != isConnected) {
                 Log.i(TAG, "Capacidades de rede mudaram. Internet: " + hasInternet);
                 ConnectivityManager.this.notifyListeners(isConnected);
             }
        }
    }
    
    public interface ConnectivityTestCallback {
        void onTestResult(boolean success);
    }
}