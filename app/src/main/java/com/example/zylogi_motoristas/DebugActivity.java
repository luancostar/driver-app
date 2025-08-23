package com.example.zylogi_motoristas;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.example.zylogi_motoristas.offline.SyncManager;

public class DebugActivity extends Activity {
    private static final String TAG = "DebugActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "DebugActivity iniciada");
        
        // Deletar operação com ID 2
        SyncManager syncManager = SyncManager.getInstance(this);
        syncManager.deleteOperationById(2);
        
        Log.i(TAG, "Comando de deleção executado");
        
        // Fechar a activity
        finish();
    }
}