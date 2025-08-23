package com.example.zylogi_motoristas.offline;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * DAO para gerenciar operações pendentes no banco de dados local
 */
@Dao
public interface PendingOperationDao {
    
    /**
     * Insere uma nova operação pendente
     */
    @Insert
    long insert(PendingOperation operation);
    
    /**
     * Atualiza uma operação existente
     */
    @Update
    void updateOperation(PendingOperation operation);
    
    /**
     * Reseta o contador de retry de todas as operações falhadas
     */
    @Query("UPDATE pending_operations SET retry_count = 0, last_error = null WHERE retry_count >= 3")
    void resetFailedOperations();
    
    /**
     * Reseta o contador de retry de uma operação específica
     */
    @Query("UPDATE pending_operations SET retry_count = 0, last_error = null WHERE id = :operationId")
    void resetOperationRetryCount(int operationId);
    
    /**
     * Atualiza o occurrenceId de uma operação específica
     */
    @Query("UPDATE pending_operations SET occurrence_id = :occurrenceId WHERE id = :operationId")
    void updateOccurrenceId(int operationId, String occurrenceId);
    
    /**
     * Remove uma operação pendente
     */
    @Delete
    void delete(PendingOperation operation);
    
    /**
     * Remove uma operação pelo ID
     */
    @Query("DELETE FROM pending_operations WHERE id = :id")
    void deleteById(int id);
    
    /**
     * Remove todas as operações de um pickup específico
     */
    @Query("DELETE FROM pending_operations WHERE pickup_id = :pickupId")
    void deleteByPickupId(String pickupId);
    
    /**
     * Obtém todas as operações pendentes ordenadas por data de criação
     */
    @Query("SELECT * FROM pending_operations ORDER BY created_at ASC")
    List<PendingOperation> getAllPendingOperations();
    
    /**
     * Obtém operações pendentes que devem ser reprocessadas
     */
    @Query("SELECT * FROM pending_operations WHERE retry_count < 10 ORDER BY created_at ASC")
    List<PendingOperation> getRetryableOperations();
    
    /**
     * Obtém operações por tipo
     */
    @Query("SELECT * FROM pending_operations WHERE operation_type = :operationType ORDER BY created_at ASC")
    List<PendingOperation> getOperationsByType(String operationType);
    
    /**
     * Obtém uma operação específica por pickup ID
     */
    @Query("SELECT * FROM pending_operations WHERE pickup_id = :pickupId LIMIT 1")
    PendingOperation getOperationByPickupId(String pickupId);
    
    /**
     * Conta o número total de operações pendentes
     */
    @Query("SELECT COUNT(*) FROM pending_operations")
    int getPendingOperationsCount();
    
    /**
     * Conta operações pendentes por tipo
     */
    @Query("SELECT COUNT(*) FROM pending_operations WHERE operation_type = :operationType")
    int getPendingOperationsCountByType(String operationType);
    
    /**
     * Obtém operações que falharam múltiplas vezes (para análise)
     */
    @Query("SELECT * FROM pending_operations WHERE retry_count >= 3 ORDER BY retry_count DESC")
    List<PendingOperation> getFailedOperations();
    
    /**
     * Remove operações antigas (mais de 7 dias) que falharam muitas vezes
     */
    @Query("DELETE FROM pending_operations WHERE retry_count >= 5 AND created_at < :cutoffTime")
    void cleanupOldFailedOperations(long cutoffTime);
    
    /**
     * Obtém o tamanho total das imagens armazenadas (para monitoramento)
     */
    @Query("SELECT SUM(LENGTH(driver_attachment_base64)) FROM pending_operations WHERE driver_attachment_base64 IS NOT NULL")
    Long getTotalImageDataSize();
    
    /**
     * Incrementa o contador de retry para uma operação específica
     */
    @Query("UPDATE pending_operations SET retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    void incrementRetryCount(int id, String error);
    
    /**
     * Verifica se existe uma operação para um pickup específico
     */
    @Query("SELECT EXISTS(SELECT 1 FROM pending_operations WHERE pickup_id = :pickupId)")
    boolean hasOperationForPickup(String pickupId);
    
    /**
     * Obtém uma operação pelo ID
     */
    @Query("SELECT * FROM pending_operations WHERE id = :id LIMIT 1")
    PendingOperation getOperationById(long id);
}