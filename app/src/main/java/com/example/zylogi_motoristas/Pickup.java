package com.example.zylogi_motoristas;

import com.google.gson.annotations.SerializedName;

public class Pickup {

    // ADICIONADO: Declaração da variável 'id'
    @SerializedName("id")
    private String id;

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
}