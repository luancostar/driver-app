package com.example.zylogi_motoristas.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

/**
 * Entidade que representa uma operação pendente de sincronização
 * Armazena dados de coletas finalizadas offline para envio posterior
 */
@Entity(tableName = "pending_operations")
public class PendingOperation {
    
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "pickup_id")
    public String pickupId;
    
    @ColumnInfo(name = "operation_type")
    public String operationType; // "COMPLETED" ou "NOT_COMPLETED"
    
    @ColumnInfo(name = "observation_driver")
    public String observationDriver;
    
    @ColumnInfo(name = "occurrence_id")
    public String occurrenceId;
    
    @ColumnInfo(name = "driver_attachment_base64")
    public String driverAttachmentBase64; // Foto em Base64
    
    @ColumnInfo(name = "driver_number_packages")
    public Integer driverNumberPackages;
    
    @ColumnInfo(name = "completion_date")
    public String completionDate;
    
    @ColumnInfo(name = "created_at")
    public long createdAt; // timestamp
    
    @ColumnInfo(name = "retry_count")
    public int retryCount = 0;
    
    @ColumnInfo(name = "last_error")
    public String lastError;
    
    // Construtor vazio necessário para Room
    public PendingOperation() {
        this.createdAt = System.currentTimeMillis();
    }
    
    // Construtor com parâmetros principais
    @androidx.room.Ignore
    public PendingOperation(String pickupId, String operationType, String observationDriver, 
                          String occurrenceId, String driverAttachmentBase64, 
                          Integer driverNumberPackages, String completionDate) {
        this.pickupId = pickupId;
        this.operationType = operationType;
        this.observationDriver = observationDriver;
        this.occurrenceId = occurrenceId;
        this.driverAttachmentBase64 = driverAttachmentBase64;
        this.driverNumberPackages = driverNumberPackages;
        this.completionDate = completionDate;
        this.createdAt = System.currentTimeMillis();
        this.retryCount = 0;
    }
    
    // Getters e Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getPickupId() { return pickupId; }
    public void setPickupId(String pickupId) { this.pickupId = pickupId; }
    
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    
    public String getObservationDriver() { return observationDriver; }
    public void setObservationDriver(String observationDriver) { this.observationDriver = observationDriver; }
    
    public String getOccurrenceId() { return occurrenceId; }
    public void setOccurrenceId(String occurrenceId) { this.occurrenceId = occurrenceId; }
    
    public String getDriverAttachmentBase64() { return driverAttachmentBase64; }
    public void setDriverAttachmentBase64(String driverAttachmentBase64) { this.driverAttachmentBase64 = driverAttachmentBase64; }
    
    public Integer getDriverNumberPackages() { return driverNumberPackages; }
    public void setDriverNumberPackages(Integer driverNumberPackages) { this.driverNumberPackages = driverNumberPackages; }
    
    public String getCompletionDate() { return completionDate; }
    public void setCompletionDate(String completionDate) { this.completionDate = completionDate; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    
    /**
     * Incrementa o contador de tentativas e atualiza o último erro
     */
    public void incrementRetryCount(String error) {
        this.retryCount++;
        this.lastError = error;
    }
    
    /**
     * Verifica se a operação deve ser reprocessada baseado no número de tentativas
     */
    public boolean shouldRetry() {
        return retryCount < 5; // Máximo 5 tentativas
    }
    
    @Override
    public String toString() {
        return "PendingOperation{" +
                "id=" + id +
                ", pickupId='" + pickupId + '\'' +
                ", operationType='" + operationType + '\'' +
                ", retryCount=" + retryCount +
                ", createdAt=" + createdAt +
                '}';
    }
}