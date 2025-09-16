package com.example.zylogi_motoristas;

import com.google.gson.annotations.SerializedName;

public class Pickup {

    // ADICIONADO: Declaração da variável 'id'
    @SerializedName("id")
    private String id;

    // ADICIONADO: Declaração da variável 'referenceId'
    @SerializedName("referenceId")
    private String referenceId;

    // ADICIONADO: Declaração da variável 'status'
    @SerializedName("status")
    private String status;

    @SerializedName("isFragile")
    private boolean isFragile;

    @SerializedName("observation")
    private String observation;

    @SerializedName("client")
    private Client client;

    @SerializedName("clientAddress")
    private ClientAddress clientAddress;

    // ADICIONADO: Campo para data de agendamento
    @SerializedName("scheduledPickupDate")  // CORREÇÃO: era "scheduledDate"
    private String scheduledDate;

    // ADICIONADO: Campo para pickup route ID
    @SerializedName("pickupRouteId")
    private String pickupRouteId;

    // ADICIONADO: Campo para vehicle ID
    @SerializedName("vehicleId")
    private String vehicleId;

    // ADICIONADO: Campo para quantidade de itens coletados pelo motorista
    @SerializedName("driverNumberPackages")
    private Integer driverNumberPackages;

    // Getters
    public boolean isFragile() { return isFragile; }
    public String getObservation() { return observation; }
    public Client getClient() { return client; }
    public ClientAddress getClientAddress() { return clientAddress; }

    // CORRIGIDO: Agora retorna o valor da variável 'status'
    public String getStatus() {
        return status;
    }

    // CORRIGIDO: Agora retorna o valor da variável 'id'
    public String getId() {
        return id;
    }

    // ADICIONADO: Getter para data de agendamento
    public String getScheduledDate() {
        return scheduledDate;
    }

    // ADICIONADO: Getter para referenceId
    public String getReferenceId() {
        return referenceId;
    }

    // ADICIONADO: Getter para pickupRouteId
    public String getPickupRouteId() {
        return pickupRouteId;
    }

    // ADICIONADO: Getter para vehicleId
    public String getVehicleId() {
        return vehicleId;
    }

    // ADICIONADO: Getter para driverNumberPackages
    public Integer getDriverNumberPackages() {
        return driverNumberPackages;
    }
}