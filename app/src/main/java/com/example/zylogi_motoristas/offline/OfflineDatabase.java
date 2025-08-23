package com.example.zylogi_motoristas.offline;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Banco de dados Room para armazenamento offline
 */
@Database(
    entities = {PendingOperation.class, PickupEntity.class},
    version = 2,
    exportSchema = false
)
public abstract class OfflineDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "offline_operations.db";
    private static volatile OfflineDatabase INSTANCE;
    
    /**
     * Obtém o DAO para operações pendentes
     */
    public abstract PendingOperationDao pendingOperationDao();
    
    /**
     * Obtém o DAO para coletas armazenadas localmente
     */
    public abstract PickupDao pickupDao();
    
    /**
     * Obtém a instância singleton do banco de dados
     */
    public static OfflineDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (OfflineDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            OfflineDatabase.class,
                            DATABASE_NAME
                    )
                    .addCallback(roomCallback)
                    .addMigrations(MIGRATION_1_2)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Callback executado quando o banco é criado
     */
    private static RoomDatabase.Callback roomCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(SupportSQLiteDatabase db) {
            super.onCreate(db);
            // Aqui podemos executar operações iniciais se necessário
        }
        
        @Override
        public void onOpen(SupportSQLiteDatabase db) {
            super.onOpen(db);
            // Executado sempre que o banco é aberto
        }
    };
    
    /**
     * Migração da versão 1 para 2 - Adiciona tabela de coletas em cache
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Cria a tabela para armazenar coletas localmente
            database.execSQL("CREATE TABLE IF NOT EXISTS cached_pickups (" +
                    "id TEXT PRIMARY KEY NOT NULL, " +
                    "reference_id TEXT, " +
                    "scheduled_date TEXT, " +
                    "status TEXT, " +
                    "is_fragile INTEGER NOT NULL, " +
                    "observation TEXT, " +
                    "pickup_route_id TEXT, " +
                    "vehicle_id TEXT, " +
                    "driver_number_packages INTEGER, " +
                    "client_data TEXT, " +
                    "client_address_data TEXT, " +
                    "driver_id TEXT, " +
                    "cached_at INTEGER NOT NULL, " +
                    "last_updated INTEGER NOT NULL" +
                    ")");
            
            // Cria índices para melhor performance
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_pickups_driver_id ON cached_pickups(driver_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_pickups_scheduled_date ON cached_pickups(scheduled_date)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_pickups_status ON cached_pickups(status)");
        }
    };
    
    /**
     * Limpa a instância do banco (útil para testes)
     */
    public static void clearInstance() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
    
    /**
     * Executa limpeza de dados antigos
     * Remove operações que falharam muitas vezes e são antigas
     */
    public void performMaintenance() {
        new Thread(() -> {
            try {
                // Remove operações com mais de 7 dias que falharam 5+ vezes
                long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
                pendingOperationDao().cleanupOldFailedOperations(cutoffTime);
                
                // Log da manutenção
                android.util.Log.i("OfflineDatabase", "Manutenção do banco executada");
            } catch (Exception e) {
                android.util.Log.e("OfflineDatabase", "Erro na manutenção do banco", e);
            }
        }).start();
    }
    
    /**
     * Obtém estatísticas do banco para monitoramento
     */
    public void logDatabaseStats() {
        new Thread(() -> {
            try {
                PendingOperationDao dao = pendingOperationDao();
                int totalOperations = dao.getPendingOperationsCount();
                int completedOperations = dao.getPendingOperationsCountByType("COMPLETED");
                int notCompletedOperations = dao.getPendingOperationsCountByType("NOT_COMPLETED");
                Long totalImageSize = dao.getTotalImageDataSize();
                
                android.util.Log.i("OfflineDatabase", 
                    String.format("Stats - Total: %d, Completed: %d, NotCompleted: %d, ImageSize: %d bytes",
                        totalOperations, completedOperations, notCompletedOperations, 
                        totalImageSize != null ? totalImageSize : 0));
            } catch (Exception e) {
                android.util.Log.e("OfflineDatabase", "Erro ao obter estatísticas", e);
            }
        }).start();
    }
}