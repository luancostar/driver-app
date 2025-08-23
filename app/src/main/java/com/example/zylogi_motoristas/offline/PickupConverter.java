package com.example.zylogi_motoristas.offline;

import com.example.zylogi_motoristas.Pickup;
import com.example.zylogi_motoristas.Client;
import com.example.zylogi_motoristas.ClientAddress;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe utilitária para converter entre Pickup e PickupEntity
 */
public class PickupConverter {
    
    private static final String TAG = "PickupConverter";
    private static final Gson gson = new Gson();
    
    /**
     * Converte um objeto Pickup para PickupEntity
     */
    public static PickupEntity toEntity(Pickup pickup, String driverId) {
        if (pickup == null) {
            return null;
        }
        
        PickupEntity entity = new PickupEntity();
        entity.setId(pickup.getId());
        entity.setReferenceId(pickup.getReferenceId());
        
        // Log para depurar a data agendada
        String scheduledDate = pickup.getScheduledDate();
        Log.d(TAG, "Convertendo coleta ID: " + pickup.getId() + ", Data agendada original: '" + scheduledDate + "'");
        
        entity.setScheduledDate(scheduledDate);
        entity.setStatus(pickup.getStatus());
        entity.setFragile(pickup.isFragile());
        entity.setObservation(pickup.getObservation());
        entity.setPickupRouteId(pickup.getPickupRouteId());
        entity.setVehicleId(pickup.getVehicleId());
        entity.setDriverNumberPackages(pickup.getDriverNumberPackages());
        entity.setDriverId(driverId);
        
        // Serializa objetos complexos para JSON
        try {
            if (pickup.getClient() != null) {
                entity.setClientData(gson.toJson(pickup.getClient()));
            }
            if (pickup.getClientAddress() != null) {
                entity.setClientAddressData(gson.toJson(pickup.getClientAddress()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao serializar dados do cliente", e);
        }
        
        return entity;
    }
    
    /**
     * Converte um PickupEntity para objeto Pickup
     */
    public static Pickup fromEntity(PickupEntity entity) {
        if (entity == null) {
            return null;
        }
        
        try {
            Pickup pickup = new Pickup();
            
            // Define campos básicos usando reflection com tratamento individual
            safeSetField(pickup, "id", entity.getId());
            safeSetField(pickup, "referenceId", entity.getReferenceId());
            safeSetField(pickup, "scheduledDate", entity.getScheduledDate());
            safeSetField(pickup, "status", entity.getStatus());
            safeSetField(pickup, "isFragile", entity.isFragile());
            safeSetField(pickup, "observation", entity.getObservation());
            safeSetField(pickup, "pickupRouteId", entity.getPickupRouteId());
            safeSetField(pickup, "vehicleId", entity.getVehicleId());
            safeSetField(pickup, "driverNumberPackages", entity.getDriverNumberPackages());
            
            // Deserializa objetos complexos do JSON com tratamento robusto
            if (entity.getClientData() != null && !entity.getClientData().trim().isEmpty()) {
                try {
                    Client client = gson.fromJson(entity.getClientData(), Client.class);
                    if (client != null) {
                        safeSetField(pickup, "client", client);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Erro ao deserializar dados do cliente, continuando sem cliente: " + e.getMessage());
                }
            }
            
            if (entity.getClientAddressData() != null && !entity.getClientAddressData().trim().isEmpty()) {
                try {
                    ClientAddress clientAddress = gson.fromJson(entity.getClientAddressData(), ClientAddress.class);
                    if (clientAddress != null) {
                        safeSetField(pickup, "clientAddress", clientAddress);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Erro ao deserializar dados do endereço, continuando sem endereço: " + e.getMessage());
                }
            }
            
            return pickup;
            
        } catch (Exception e) {
            Log.e(TAG, "Erro crítico ao converter PickupEntity para Pickup: " + e.getMessage(), e);
            // Retorna um objeto Pickup básico em vez de null para evitar crashes
            try {
                Pickup fallbackPickup = new Pickup();
                safeSetField(fallbackPickup, "id", entity.getId() != null ? entity.getId() : "unknown");
                safeSetField(fallbackPickup, "status", entity.getStatus() != null ? entity.getStatus() : "UNKNOWN");
                return fallbackPickup;
            } catch (Exception fallbackError) {
                Log.e(TAG, "Erro ao criar pickup de fallback", fallbackError);
                return null;
            }
        }
    }
    
    /**
     * Converte uma lista de Pickup para lista de PickupEntity
     */
    public static List<PickupEntity> toEntityList(List<Pickup> pickups, String driverId) {
        if (pickups == null) {
            return new ArrayList<>();
        }
        
        List<PickupEntity> entities = new ArrayList<>();
        for (Pickup pickup : pickups) {
            PickupEntity entity = toEntity(pickup, driverId);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }
    
    /**
     * Converte uma lista de PickupEntity para lista de Pickup
     */
    public static List<Pickup> fromEntityList(List<PickupEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        
        List<Pickup> pickups = new ArrayList<>();
        for (PickupEntity entity : entities) {
            Pickup pickup = fromEntity(entity);
            if (pickup != null) {
                pickups.add(pickup);
            }
        }
        return pickups;
    }
    
    /**
     * Define um campo usando reflection
     */
    private static void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao definir campo " + fieldName, e);
        }
    }
    
    /**
     * Versão mais segura do setField que não lança exceções
     */
    private static void safeSetField(Object obj, String fieldName, Object value) {
        try {
            if (obj == null || fieldName == null) {
                return;
            }
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException e) {
            Log.w(TAG, "Campo não encontrado: " + fieldName);
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Acesso negado ao campo: " + fieldName);
        } catch (Exception e) {
            Log.w(TAG, "Erro ao definir campo " + fieldName + ": " + e.getMessage());
        }
    }
}