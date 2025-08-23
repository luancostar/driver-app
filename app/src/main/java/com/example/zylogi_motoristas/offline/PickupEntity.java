package com.example.zylogi_motoristas.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

/**
 * Entidade que representa uma coleta armazenada localmente
 * Permite acesso offline às coletas já baixadas
 */
@Entity(tableName = "cached_pickups")
public class PickupEntity {
    
    @PrimaryKey
    @NonNull
    public String id;
    
    @ColumnInfo(name = "reference_id")
    public String referenceId;
    
    @ColumnInfo(name = "scheduled_date")
    public String scheduledDate;
    
    @ColumnInfo(name = "status")
    public String status;
    
    @ColumnInfo(name = "is_fragile")
    public boolean isFragile;
    
    @ColumnInfo(name = "observation")
    public String observation;
    
    @ColumnInfo(name = "pickup_route_id")
    public String pickupRouteId;
    
    @ColumnInfo(name = "vehicle_id")
    public String vehicleId;
    
    @ColumnInfo(name = "driver_number_packages")
    public Integer driverNumberPackages;
    
    // Dados do cliente (JSON serializado para simplificar)
    @ColumnInfo(name = "client_data")
    public String clientData;
    
    // Dados do endereço do cliente (JSON serializado)
    @ColumnInfo(name = "client_address_data")
    public String clientAddressData;
    
    @ColumnInfo(name = "driver_id")
    public String driverId;
    
    @ColumnInfo(name = "cached_at")
    public long cachedAt; // timestamp de quando foi armazenado
    
    @ColumnInfo(name = "last_updated")
    public long lastUpdated; // timestamp da última atualização
    
    // Construtor vazio necessário para Room
    public PickupEntity() {}
    
    // Construtor completo
    public PickupEntity(@NonNull String id, String referenceId, String scheduledDate, 
                       String status, boolean isFragile, String observation,
                       String pickupRouteId, String vehicleId, Integer driverNumberPackages,
                       String clientData, String clientAddressData, String driverId) {
        this.id = id;
        this.referenceId = referenceId;
        this.scheduledDate = scheduledDate;
        this.status = status;
        this.isFragile = isFragile;
        this.observation = observation;
        this.pickupRouteId = pickupRouteId;
        this.vehicleId = vehicleId;
        this.driverNumberPackages = driverNumberPackages;
        this.clientData = clientData;
        this.clientAddressData = clientAddressData;
        this.driverId = driverId;
        this.cachedAt = System.currentTimeMillis();
        this.lastUpdated = System.currentTimeMillis();
    }
    
    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    
    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public boolean isFragile() { return isFragile; }
    public void setFragile(boolean fragile) { isFragile = fragile; }
    
    public String getObservation() { return observation; }
    public void setObservation(String observation) { this.observation = observation; }
    
    public String getPickupRouteId() { return pickupRouteId; }
    public void setPickupRouteId(String pickupRouteId) { this.pickupRouteId = pickupRouteId; }
    
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    
    public Integer getDriverNumberPackages() { return driverNumberPackages; }
    public void setDriverNumberPackages(Integer driverNumberPackages) { this.driverNumberPackages = driverNumberPackages; }
    
    public String getClientData() { return clientData; }
    public void setClientData(String clientData) { this.clientData = clientData; }
    
    public String getClientAddressData() { return clientAddressData; }
    public void setClientAddressData(String clientAddressData) { this.clientAddressData = clientAddressData; }
    
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    
    public long getCachedAt() { return cachedAt; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
    
    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}