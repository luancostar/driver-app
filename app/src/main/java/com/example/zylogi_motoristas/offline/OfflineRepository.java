package com.example.zylogi_motoristas.offline;

import android.content.Context;
import android.util.Log;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.zylogi_motoristas.Pickup;
import com.example.zylogi_motoristas.Occurrence;

/**
 * Repositório para gerenciar operações offline
 * Interface entre ViewModels e banco de dados local
 */
public class OfflineRepository {
    
    private static final String TAG = "OfflineRepository";
    private static volatile OfflineRepository INSTANCE;
    
    private final PendingOperationDao dao;
    private final PickupDao pickupDao;
    private final OccurrenceDao occurrenceDao;
    private final ExecutorService executor;
    
    private OfflineRepository(Context context) {
        OfflineDatabase database = OfflineDatabase.getInstance(context);
        dao = database.pendingOperationDao();
        pickupDao = database.pickupDao();
        occurrenceDao = database.occurrenceDao();
        executor = Executors.newFixedThreadPool(2);
    }
    
    /**
     * Obtém a instância singleton do repositório
     */
    public static OfflineRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (OfflineRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new OfflineRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Salva uma operação de finalização offline
     */
    public void saveOfflineOperation(String pickupId, String operationType, 
                                   String observationDriver, String occurrenceId,
                                   String driverAttachmentBase64, Integer driverNumberPackages,
                                   String completionDate, OperationSaveCallback callback) {
        
        executor.execute(() -> {
            try {
                // Verifica se já existe uma operação para este pickup
                if (dao.hasOperationForPickup(pickupId)) {
                    Log.w(TAG, "Operação já existe para pickup: " + pickupId);
                    if (callback != null) {
                        callback.onError("Operação já existe para este pickup");
                    }
                    return;
                }
                
                // Comprime a imagem se necessário
                String compressedImage = compressImageIfNeeded(driverAttachmentBase64);
                
                // Cria a operação
                PendingOperation operation = new PendingOperation(
                    pickupId, operationType, observationDriver, occurrenceId,
                    compressedImage, driverNumberPackages, completionDate
                );
                
                // Salva no banco
                long id = dao.insert(operation);
                
                Log.i(TAG, String.format("Operação salva offline - ID: %d, Pickup: %s, Tipo: %s", 
                    id, pickupId, operationType));
                
                if (callback != null) {
                    callback.onSuccess(id);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Erro ao salvar operação offline", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Salva uma operação offline (método simplificado)
     */
    public void saveOperation(String pickupId, String operationType, 
                            String observationDriver, String occurrenceId,
                            String driverAttachmentBase64, Integer driverNumberPackages,
                            OperationSaveCallback callback) {
        saveOfflineOperation(pickupId, operationType, observationDriver, occurrenceId,
                           driverAttachmentBase64, driverNumberPackages, 
                           String.valueOf(System.currentTimeMillis()), callback);
    }
    
    /**
     * Obtém todas as operações pendentes
     */
    public void getAllPendingOperations(OperationListCallback callback) {
        executor.execute(() -> {
            try {
                List<PendingOperation> operations = dao.getAllPendingOperations();
                Log.i(TAG, "Operações pendentes encontradas: " + operations.size());
                
                if (callback != null) {
                    callback.onSuccess(operations);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao obter operações pendentes", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Obtém operações que podem ser reprocessadas
     */
    public void getRetryableOperations(OperationListCallback callback) {
        executor.execute(() -> {
            try {
                List<PendingOperation> operations = dao.getRetryableOperations();
                Log.i(TAG, "Operações para retry encontradas: " + operations.size());
                
                if (callback != null) {
                    callback.onSuccess(operations);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao obter operações para retry", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Remove uma operação após sincronização bem-sucedida
     */
    public void removeOperation(int operationId, OperationCallback callback) {
        executor.execute(() -> {
            try {
                dao.deleteById(operationId);
                Log.i(TAG, "Operação removida após sincronização: " + operationId);
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao remover operação", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Incrementa o contador de tentativas de uma operação
     */
    public void incrementRetryCount(long operationId, OperationCallback callback) {
        executor.execute(() -> {
            try {
                PendingOperation operation = dao.getOperationById(operationId);
                if (operation != null) {
                    operation.incrementRetryCount("Erro de sincronização");
                    dao.updateOperation(operation);
                    Log.d(TAG, "Contador de retry incrementado para operação: " + operationId + 
                           " (tentativa " + operation.getRetryCount() + ")");
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    String error = "Operação não encontrada: " + operationId;
                    Log.w(TAG, error);
                    if (callback != null) {
                        callback.onError(error);
                    }
                }
            } catch (Exception e) {
                String error = "Erro ao incrementar contador de retry: " + e.getMessage();
                Log.e(TAG, error);
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }
    
    /**
     * Marca uma operação como falhada e incrementa o contador de retry
     */
    public void markOperationAsFailed(int operationId, String error, OperationCallback callback) {
        executor.execute(() -> {
            try {
                dao.incrementRetryCount(operationId, error);
                Log.w(TAG, String.format("Operação marcada como falhada - ID: %d, Erro: %s", 
                    operationId, error));
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao marcar operação como falhada", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Obtém o número de operações pendentes
     */
    public void getPendingOperationsCount(CountCallback callback) {
        executor.execute(() -> {
            try {
                int count = dao.getPendingOperationsCount();
                if (callback != null) {
                    callback.onCount(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao obter contagem de operações", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Verifica se existe operação pendente para um pickup
     */
    public void hasOperationForPickup(String pickupId, BooleanCallback callback) {
        executor.execute(() -> {
            try {
                boolean exists = dao.hasOperationForPickup(pickupId);
                if (callback != null) {
                    callback.onResult(exists);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar operação para pickup", e);
                if (callback != null) {
                    callback.onResult(false);
                }
            }
        });
    }
    
    /**
     * Comprime a imagem Base64 se for muito grande
     */
    private String compressImageIfNeeded(String base64Image) {
        if (base64Image == null || base64Image.length() < 500000) { // Menos de ~500KB
            return base64Image;
        }
        
        Log.w(TAG, "Imagem grande detectada: " + base64Image.length() + " caracteres");
        return compressImageBase64(base64Image);
    }
    
    // Compressão de imagem para economizar espaço no banco offline
    private String compressImageBase64(String base64Image) {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            return base64Image;
        }
        
        try {
            // Decodificar Base64 para bitmap
            byte[] imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
            android.graphics.Bitmap originalBitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
            if (originalBitmap == null) {
                android.util.Log.w("OfflineRepository", "Falha ao decodificar imagem para compressão");
                return base64Image;
            }
            
            // Calcular novo tamanho (máximo 1024px na maior dimensão)
            int maxDimension = 1024;
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();
            
            float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
            
            if (scale >= 1.0f) {
                // Imagem já é pequena, apenas recomprimir com qualidade menor
                return compressBitmapToBase64(originalBitmap, 70);
            }
            
            // Redimensionar
            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);
            
            android.graphics.Bitmap resizedBitmap = android.graphics.Bitmap.createScaledBitmap(
                originalBitmap, newWidth, newHeight, true);
            
            // Comprimir com qualidade 70%
            String compressedBase64 = compressBitmapToBase64(resizedBitmap, 70);
            
            // Limpar recursos
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            if (!resizedBitmap.isRecycled()) {
                resizedBitmap.recycle();
            }
            
            android.util.Log.d("OfflineRepository", 
                String.format("Imagem comprimida: %d -> %d bytes (%.1f%% redução)",
                    base64Image.length(), compressedBase64.length(),
                    (1.0f - (float)compressedBase64.length() / base64Image.length()) * 100));
            
            return compressedBase64;
            
        } catch (Exception e) {
            android.util.Log.e("OfflineRepository", "Erro na compressão de imagem: " + e.getMessage());
            return base64Image; // Retorna original em caso de erro
        }
    }
    
    private String compressBitmapToBase64(android.graphics.Bitmap bitmap, int quality) {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream);
        byte[] compressedBytes = outputStream.toByteArray();
        
        try {
            outputStream.close();
        } catch (java.io.IOException e) {
            android.util.Log.w("OfflineRepository", "Erro ao fechar stream: " + e.getMessage());
        }
        
        return android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP);
    }
    
    /**
     * Executa limpeza de dados antigos
     */
    public void performMaintenance() {
        executor.execute(() -> {
            long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 dias
            dao.cleanupOldFailedOperations(cutoffTime);
            // Limpa também coletas antigas do cache
            pickupDao.deleteOldPickups(cutoffTime);
        });
    }
    
    /**
     * Realiza manutenção do banco de dados
     * Remove operações muito antigas e limpa dados desnecessários
     */
    public void performMaintenance(OperationCallback callback) {
        executor.execute(() -> {
            try {
                long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 dias
                dao.cleanupOldFailedOperations(cutoffTime);
                // Limpa também coletas antigas do cache
                pickupDao.deleteOldPickups(cutoffTime);
                Log.i(TAG, "Manutenção do banco de dados concluída");
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro durante manutenção do banco", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Reseta operações falhadas para permitir nova tentativa de sincronização
     */
    public void resetFailedOperations(OperationCallback callback) {
        executor.execute(() -> {
            try {
                dao.resetFailedOperations();
                Log.i(TAG, "Operações falhadas resetadas para nova tentativa");
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao resetar operações falhadas", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Reseta uma operação específica para permitir nova tentativa
     */
    public void resetOperationRetryCount(int operationId, OperationCallback callback) {
        executor.execute(() -> {
            try {
                dao.resetOperationRetryCount(operationId);
                Log.i(TAG, "Operação " + operationId + " resetada para nova tentativa");
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao resetar operação " + operationId, e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Atualiza o occurrenceId de uma operação específica
     */
    public void updateOperationOccurrenceId(int operationId, String occurrenceId, OperationCallback callback) {
        executor.execute(() -> {
            try {
                dao.updateOccurrenceId(operationId, occurrenceId);
                Log.i(TAG, "OccurrenceId da operação " + operationId + " atualizado para: " + occurrenceId);
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao atualizar occurrenceId da operação " + operationId, e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Remove uma operação específica por ID
     */
    public void deleteOperationById(int operationId, OperationCallback callback) {
        executor.execute(() -> {
            try {
                dao.deleteById(operationId);
                Log.i(TAG, "Operação " + operationId + " removida com sucesso");
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao remover operação " + operationId, e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    // ==================== MÉTODOS DE CACHE DE COLETAS ====================
    
    /**
     * Salva coletas no cache local
     */
    public void cachePickups(List<Pickup> pickups, String driverId, PickupCacheCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "=== SALVANDO COLETAS NO CACHE ===");
                Log.d(TAG, "Driver ID: " + driverId);
                Log.d(TAG, "Número de coletas para salvar: " + pickups.size());
                
                // Log detalhado de cada coleta antes da conversão
                for (Pickup pickup : pickups) {
                    Log.d(TAG, "Coleta original - ID: " + pickup.getId() + ", Data agendada: '" + pickup.getScheduledDate() + "', Status: " + pickup.getStatus());
                    // ADICIONADO: Log extra para debug da data
                    if (pickup.getScheduledDate() == null || pickup.getScheduledDate().trim().isEmpty()) {
                        Log.w(TAG, "ATENÇÃO: Coleta " + pickup.getId() + " tem scheduledDate null/vazio - será tratada como hoje");
                    }
                }
                
                List<PickupEntity> entities = PickupConverter.toEntityList(pickups, driverId);
                
                // Log detalhado de cada entidade após a conversão
                for (PickupEntity entity : entities) {
                    Log.d(TAG, "Entidade convertida - ID: " + entity.getId() + ", Data agendada: '" + entity.getScheduledDate() + "', Status: " + entity.getStatus() + ", Driver ID: " + entity.getDriverId());
                }
                
                pickupDao.insertOrUpdateAll(entities);
                
                Log.d(TAG, "Coletas armazenadas no cache: " + entities.size());
                if (callback != null) {
                    callback.onSuccess(entities.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao armazenar coletas no cache", e);
                if (callback != null) {
                    callback.onError("Erro ao armazenar coletas: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Obtém coletas do cache local
     */
    public void getCachedPickups(String driverId, String date, PickupListCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "=== BUSCANDO COLETAS NO CACHE ===");
                Log.d(TAG, "Driver ID: " + driverId);
                Log.d(TAG, "Data solicitada: " + date);
                
                // Primeiro, vamos verificar quantas coletas existem no total para este motorista
                int totalCount = pickupDao.getPickupsCountByDriverId(driverId);
                Log.d(TAG, "Total de coletas no cache para este motorista: " + totalCount);
                
                List<PickupEntity> entities;
                if (date != null && !date.isEmpty()) {
                    Log.d(TAG, "Buscando por data específica: " + date);
                    entities = pickupDao.getPickupsByDriverIdAndDate(driverId, date);
                    Log.d(TAG, "Coletas encontradas para a data específica: " + entities.size());
                    
                    // Se não há coletas para hoje, retorna lista vazia (comportamento correto)
                    if (entities.isEmpty()) {
                        Log.d(TAG, "Nenhuma coleta encontrada para a data: " + date);
                    }
                    
                    // Debug: mostrar todas as coletas no cache
                    List<PickupEntity> allEntities = pickupDao.getPickupsByDriverId(driverId);
                    Log.d(TAG, "=== DEBUG: TODAS AS COLETAS NO CACHE ===");
                    for (PickupEntity entity : allEntities) {
                        Log.d(TAG, "Coleta ID: " + entity.getId() + ", Data agendada: '" + entity.getScheduledDate() + "', Status: " + entity.getStatus());
                    }
                } else {
                    Log.d(TAG, "Buscando todas as coletas (sem filtro de data)");
                    entities = pickupDao.getPickupsByDriverId(driverId);
                }
                
                List<Pickup> pickups = PickupConverter.fromEntityList(entities);
                
                Log.d(TAG, "Coletas recuperadas do cache: " + pickups.size());
                if (callback != null) {
                    callback.onSuccess(pickups);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao recuperar coletas do cache", e);
                if (callback != null) {
                    callback.onError("Erro ao recuperar coletas: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Atualiza o status de uma coleta no cache
     */
    public void updateCachedPickupStatus(String pickupId, String status, OperationCallback callback) {
        executor.execute(() -> {
            try {
                pickupDao.updatePickupStatus(pickupId, status, System.currentTimeMillis());
                
                Log.d(TAG, "Status da coleta atualizado no cache: " + pickupId + " -> " + status);
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao atualizar status da coleta no cache", e);
                if (callback != null) {
                    callback.onError("Erro ao atualizar status: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Verifica se existem coletas no cache
     */
    public void hasCachedPickups(String driverId, BooleanCallback callback) {
        executor.execute(() -> {
            try {
                int count = pickupDao.getPickupsCountByDriverId(driverId);
                if (callback != null) {
                    callback.onResult(count > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar coletas no cache", e);
                if (callback != null) {
                    callback.onResult(false);
                }
            }
        });
    }
    
    /**
     * Limpa o cache de coletas de um motorista
     */
    public void clearPickupCache(String driverId, OperationCallback callback) {
        executor.execute(() -> {
            try {
                pickupDao.deleteByDriverId(driverId);
                
                Log.d(TAG, "Cache de coletas limpo para motorista: " + driverId);
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao limpar cache de coletas", e);
                if (callback != null) {
                    callback.onError("Erro ao limpar cache: " + e.getMessage());
                }
            }
        });
    }
    
    // Interfaces de callback
    public interface OperationSaveCallback {
        void onSuccess(long operationId);
        void onError(String error);
    }
    
    public interface OperationListCallback {
        void onSuccess(List<PendingOperation> operations);
        void onError(String error);
    }
    
    public interface OperationCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface CountCallback {
        void onCount(int count);
        void onError(String error);
    }
    
    public interface BooleanCallback {
        void onResult(boolean result);
    }
    
    public interface PickupCacheCallback {
        void onSuccess(int count);
        void onError(String error);
    }
    
    public interface PickupListCallback {
        void onSuccess(List<Pickup> pickups);
        void onError(String error);
    }
    
    // ==================== MÉTODOS DE CACHE DE OCORRÊNCIAS ====================
    
    /**
     * Salva ocorrências no cache local
     */
    public void cacheOccurrences(List<Occurrence> occurrences, OccurrenceCacheCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "=== SALVANDO OCORRÊNCIAS NO CACHE ===");
                Log.d(TAG, "Número de ocorrências para salvar: " + occurrences.size());
                
                // Log detalhado de cada ocorrência antes da conversão
                for (Occurrence occurrence : occurrences) {
                    Log.d(TAG, "Ocorrência original - ID: " + occurrence.getId() + 
                              ", Nome: " + occurrence.getName() + 
                              ", ReferenceId: " + occurrence.getReferenceId());
                }
                
                List<OccurrenceEntity> entities = OccurrenceConverter.toEntityList(occurrences);
                
                // Log detalhado de cada entidade após a conversão
                for (OccurrenceEntity entity : entities) {
                    Log.d(TAG, "Entidade convertida - ID: " + entity.getId() + 
                              ", Nome: " + entity.getName() + 
                              ", ReferenceId: " + entity.getReferenceId());
                }
                
                occurrenceDao.insertOrUpdateAll(entities);
                
                Log.d(TAG, "Ocorrências armazenadas no cache: " + entities.size());
                if (callback != null) {
                    callback.onSuccess(entities.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao armazenar ocorrências no cache", e);
                if (callback != null) {
                    callback.onError("Erro ao armazenar ocorrências: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Obtém ocorrências do cache local
     */
    public void getCachedOccurrences(OccurrenceListCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "=== BUSCANDO OCORRÊNCIAS NO CACHE ===");
                
                List<OccurrenceEntity> entities = occurrenceDao.getActiveOccurrences();
                Log.d(TAG, "Total de ocorrências encontradas no cache: " + entities.size());
                
                List<Occurrence> occurrences = OccurrenceConverter.fromEntityList(entities);
                
                // Log das ocorrências retornadas
                for (Occurrence occurrence : occurrences) {
                    Log.d(TAG, "Ocorrência do cache - ID: " + occurrence.getId() + 
                              ", Nome: " + occurrence.getName() + 
                              ", ReferenceId: " + occurrence.getReferenceId());
                }
                
                if (callback != null) {
                    callback.onSuccess(occurrences);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao buscar ocorrências do cache", e);
                if (callback != null) {
                    callback.onError("Erro ao buscar ocorrências: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Obtém uma ocorrência específica por referenceId
     */
    public void getCachedOccurrenceByReferenceId(String referenceId, OccurrenceCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Buscando ocorrência com referenceId: " + referenceId);
                
                OccurrenceEntity entity = occurrenceDao.getOccurrenceByReferenceId(referenceId);
                
                if (entity != null) {
                    Occurrence occurrence = OccurrenceConverter.fromEntity(entity);
                    Log.d(TAG, "Ocorrência encontrada: " + occurrence.getName());
                    if (callback != null) {
                        callback.onSuccess(occurrence);
                    }
                } else {
                    Log.d(TAG, "Ocorrência não encontrada no cache");
                    if (callback != null) {
                        callback.onError("Ocorrência não encontrada");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao buscar ocorrência por referenceId", e);
                if (callback != null) {
                    callback.onError("Erro ao buscar ocorrência: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Verifica se existem ocorrências no cache
     */
    public void hasOccurrencesInCache(BooleanCallback callback) {
        executor.execute(() -> {
            try {
                int count = occurrenceDao.getActiveOccurrencesCount();
                boolean hasOccurrences = count > 0;
                Log.d(TAG, "Ocorrências no cache: " + count + " (tem ocorrências: " + hasOccurrences + ")");
                if (callback != null) {
                    callback.onResult(hasOccurrences);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar ocorrências no cache", e);
                if (callback != null) {
                    callback.onResult(false);
                }
            }
        });
    }
    
    /**
     * Limpa o cache de ocorrências
     */
    public void clearOccurrenceCache(OperationCallback callback) {
        executor.execute(() -> {
            try {
                occurrenceDao.clearAllOccurrences();
                Log.d(TAG, "Cache de ocorrências limpo");
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao limpar cache de ocorrências", e);
                if (callback != null) {
                    callback.onError("Erro ao limpar cache: " + e.getMessage());
                }
            }
        });
    }
    
    public interface OccurrenceCacheCallback {
        void onSuccess(int count);
        void onError(String error);
    }
    
    public interface OccurrenceListCallback {
        void onSuccess(List<Occurrence> occurrences);
        void onError(String error);
    }
    
    public interface OccurrenceCallback {
        void onSuccess(Occurrence occurrence);
        void onError(String error);
    }
}