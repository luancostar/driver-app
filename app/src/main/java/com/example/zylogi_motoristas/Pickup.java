package com.example.zylogi_motoristas;

import com.google.gson.annotations.SerializedName;

public class Pickup {
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
}