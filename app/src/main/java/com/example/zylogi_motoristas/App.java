package com.example.zylogi_motoristas;

import android.app.Application;
import android.util.Log;

import com.example.zylogi_motoristas.offline.ConnectivityManager;
import com.example.zylogi_motoristas.offline.SyncManager;

public class App extends Application {

    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Inicializa gerenciadores essenciais para garantir processamento em background
            ConnectivityManager.getInstance(this);
            SyncManager.getInstance(this);
            Log.i(TAG, "ConnectivityManager e SyncManager inicializados no Application");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar managers no Application", e);
        }
    }
}
