package com.example.zylogi_motoristas.offline;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * DAO para gerenciar coletas armazenadas localmente
 */
@Dao
public interface PickupDao {
    
    /**
     * Insere ou atualiza uma coleta (substitui se já existir)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(PickupEntity pickup);
    
    /**
     * Insere ou atualiza múltiplas coletas
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAll(List<PickupEntity> pickups);
    
    /**
     * Atualiza uma coleta existente
     */
    @Update
    void update(PickupEntity pickup);
    
    /**
     * Remove uma coleta
     */
    @Delete
    void delete(PickupEntity pickup);
    
    /**
     * Remove uma coleta pelo ID
     */
    @Query("DELETE FROM cached_pickups WHERE id = :pickupId")
    void deleteById(String pickupId);
    
    /**
     * Remove todas as coletas de um motorista
     */
    @Query("DELETE FROM cached_pickups WHERE driver_id = :driverId")
    void deleteByDriverId(String driverId);
    
    /**
     * Remove coletas antigas (mais de 7 dias)
     */
    @Query("DELETE FROM cached_pickups WHERE cached_at < :cutoffTime")
    void deleteOldPickups(long cutoffTime);
    
    /**
     * Remove coletas de um motorista que não são da data especificada
     */
    @Query("DELETE FROM cached_pickups WHERE driver_id = :driverId AND (scheduled_date IS NULL OR scheduled_date NOT LIKE :date || '%')")
    void deletePickupsNotFromDate(String driverId, String date);
    
    /**
     * Obtém uma coleta pelo ID
     */
    @Query("SELECT * FROM cached_pickups WHERE id = :pickupId LIMIT 1")
    PickupEntity getPickupById(String pickupId);
    
    /**
     * Obtém todas as coletas de um motorista
     */
    @Query("SELECT * FROM cached_pickups WHERE driver_id = :driverId ORDER BY scheduled_date ASC")
    List<PickupEntity> getPickupsByDriverId(String driverId);
    
    /**
     * Obtém coletas de um motorista para uma data específica
     * CORRIGIDO: Inclui coletas com scheduledDate null (assumindo que são para hoje)
     */
    @Query("SELECT * FROM cached_pickups WHERE driver_id = :driverId AND (scheduled_date LIKE :date || '%' OR scheduled_date IS NULL) ORDER BY scheduled_date ASC")
    List<PickupEntity> getPickupsByDriverIdAndDate(String driverId, String date);
    
    /**
     * Obtém coletas de um motorista em um intervalo de datas
     */
    @Query("SELECT * FROM cached_pickups WHERE driver_id = :driverId AND scheduled_date BETWEEN :startDate AND :endDate ORDER BY scheduled_date ASC")
    List<PickupEntity> getPickupsByDriverIdAndDateRange(String driverId, String startDate, String endDate);
    
    /**
     * Obtém coletas pendentes de um motorista
     */
    @Query("SELECT * FROM cached_pickups WHERE driver_id = :driverId AND status = 'PENDING' ORDER BY scheduled_date ASC")
    List<PickupEntity> getPendingPickupsByDriverId(String driverId);
    
    /**
     * Obtém todas as coletas armazenadas
     */
    @Query("SELECT * FROM cached_pickups ORDER BY scheduled_date ASC")
    List<PickupEntity> getAllPickups();
    
    /**
     * Conta o número total de coletas armazenadas
     */
    @Query("SELECT COUNT(*) FROM cached_pickups")
    int getPickupsCount();
    
    /**
     * Conta coletas de um motorista
     */
    @Query("SELECT COUNT(*) FROM cached_pickups WHERE driver_id = :driverId")
    int getPickupsCountByDriverId(String driverId);
    
    /**
     * Conta coletas pendentes de um motorista
     */
    @Query("SELECT COUNT(*) FROM cached_pickups WHERE driver_id = :driverId AND status = 'PENDING'")
    int getPendingPickupsCountByDriverId(String driverId);
    
    /**
     * Verifica se existe uma coleta específica
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_pickups WHERE id = :pickupId)")
    boolean pickupExists(String pickupId);
    
    /**
     * Atualiza o status de uma coleta
     */
    @Query("UPDATE cached_pickups SET status = :status, last_updated = :timestamp WHERE id = :pickupId")
    void updatePickupStatus(String pickupId, String status, long timestamp);
    
    /**
     * Obtém coletas que precisam ser sincronizadas (modificadas recentemente)
     */
    @Query("SELECT * FROM cached_pickups WHERE last_updated > cached_at ORDER BY last_updated DESC")
    List<PickupEntity> getModifiedPickups();
    
    /**
     * Limpa todas as coletas armazenadas
     */
    @Query("DELETE FROM cached_pickups")
    void clearAllPickups();
}