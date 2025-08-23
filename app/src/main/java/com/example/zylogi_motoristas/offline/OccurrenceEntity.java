package com.example.zylogi_motoristas.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

/**
 * Entidade que representa uma ocorrência armazenada localmente
 * Permite acesso offline às ocorrências já baixadas
 */
@Entity(tableName = "cached_occurrences")
public class OccurrenceEntity {
    
    @PrimaryKey
    @NonNull
    public String id;
    
    @ColumnInfo(name = "reference_id")
    public String referenceId;
    
    @ColumnInfo(name = "occurrence_number")
    public int occurrenceNumber;
    
    @ColumnInfo(name = "name")
    public String name;
    
    @ColumnInfo(name = "is_client_fault")
    public boolean isClientFault;
    
    @ColumnInfo(name = "send_to_app")
    public boolean sendToApp;
    
    @ColumnInfo(name = "is_activated")
    public boolean isActivated;
    
    @ColumnInfo(name = "created_at")
    public String createdAt;
    
    @ColumnInfo(name = "updated_at")
    public String updatedAt;
    
    @ColumnInfo(name = "cached_at")
    public long cachedAt; // timestamp de quando foi armazenado
    
    // Construtor vazio necessário para Room
    public OccurrenceEntity() {}
    
    // Construtor completo
    public OccurrenceEntity(@NonNull String id, String referenceId, int occurrenceNumber,
                           String name, boolean isClientFault, boolean sendToApp,
                           boolean isActivated, String createdAt, String updatedAt) {
        this.id = id;
        this.referenceId = referenceId;
        this.occurrenceNumber = occurrenceNumber;
        this.name = name;
        this.isClientFault = isClientFault;
        this.sendToApp = sendToApp;
        this.isActivated = isActivated;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cachedAt = System.currentTimeMillis();
    }
    
    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    
    public int getOccurrenceNumber() { return occurrenceNumber; }
    public void setOccurrenceNumber(int occurrenceNumber) { this.occurrenceNumber = occurrenceNumber; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public boolean isClientFault() { return isClientFault; }
    public void setClientFault(boolean clientFault) { isClientFault = clientFault; }
    
    public boolean isSendToApp() { return sendToApp; }
    public void setSendToApp(boolean sendToApp) { this.sendToApp = sendToApp; }
    
    public boolean isActivated() { return isActivated; }
    public void setActivated(boolean activated) { isActivated = activated; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    
    public long getCachedAt() { return cachedAt; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
}