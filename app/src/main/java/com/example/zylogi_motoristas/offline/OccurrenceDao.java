package com.example.zylogi_motoristas.offline;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * DAO para gerenciar ocorrências armazenadas localmente
 */
@Dao
public interface OccurrenceDao {
    
    /**
     * Insere ou atualiza uma ocorrência (substitui se já existir)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(OccurrenceEntity occurrence);
    
    /**
     * Insere ou atualiza múltiplas ocorrências
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAll(List<OccurrenceEntity> occurrences);
    
    /**
     * Atualiza uma ocorrência existente
     */
    @Update
    void update(OccurrenceEntity occurrence);
    
    /**
     * Remove uma ocorrência
     */
    @Delete
    void delete(OccurrenceEntity occurrence);
    
    /**
     * Remove uma ocorrência pelo ID
     */
    @Query("DELETE FROM cached_occurrences WHERE id = :occurrenceId")
    void deleteById(String occurrenceId);
    
    /**
     * Remove ocorrências antigas (mais de 30 dias)
     */
    @Query("DELETE FROM cached_occurrences WHERE cached_at < :cutoffTime")
    void deleteOldOccurrences(long cutoffTime);
    
    /**
     * Obtém uma ocorrência pelo ID
     */
    @Query("SELECT * FROM cached_occurrences WHERE id = :occurrenceId LIMIT 1")
    OccurrenceEntity getOccurrenceById(String occurrenceId);
    
    /**
     * Obtém uma ocorrência pelo referenceId
     */
    @Query("SELECT * FROM cached_occurrences WHERE reference_id = :referenceId LIMIT 1")
    OccurrenceEntity getOccurrenceByReferenceId(String referenceId);
    
    /**
     * Obtém todas as ocorrências ativas
     */
    @Query("SELECT * FROM cached_occurrences WHERE is_activated = 1 ORDER BY occurrence_number ASC")
    List<OccurrenceEntity> getActiveOccurrences();
    
    /**
     * Obtém todas as ocorrências armazenadas
     */
    @Query("SELECT * FROM cached_occurrences ORDER BY occurrence_number ASC")
    List<OccurrenceEntity> getAllOccurrences();
    
    /**
     * Obtém ocorrências que devem ser enviadas para o app
     */
    @Query("SELECT * FROM cached_occurrences WHERE send_to_app = 1 AND is_activated = 1 ORDER BY occurrence_number ASC")
    List<OccurrenceEntity> getAppOccurrences();
    
    /**
     * Conta o número total de ocorrências armazenadas
     */
    @Query("SELECT COUNT(*) FROM cached_occurrences")
    int getOccurrencesCount();
    
    /**
     * Conta ocorrências ativas
     */
    @Query("SELECT COUNT(*) FROM cached_occurrences WHERE is_activated = 1")
    int getActiveOccurrencesCount();
    
    /**
     * Verifica se existe uma ocorrência específica
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_occurrences WHERE id = :occurrenceId)")
    boolean occurrenceExists(String occurrenceId);
    
    /**
     * Verifica se existe uma ocorrência com referenceId específico
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_occurrences WHERE reference_id = :referenceId)")
    boolean occurrenceExistsByReferenceId(String referenceId);
    
    /**
     * Limpa todas as ocorrências armazenadas
     */
    @Query("DELETE FROM cached_occurrences")
    void clearAllOccurrences();
}