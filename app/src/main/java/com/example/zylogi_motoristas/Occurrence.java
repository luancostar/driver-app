package com.example.zylogi_motoristas;

import com.google.gson.annotations.SerializedName;

public class Occurrence {
    @SerializedName("id")
    private String id;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    @SerializedName("referenceId")
    private String referenceId;

    @SerializedName("occurrenceNumber")
    private int occurrenceNumber;

    @SerializedName("name")
    private String name;

    @SerializedName("isClientFault")
    private boolean isClientFault;

    @SerializedName("sendToApp")
    private boolean sendToApp;

    @SerializedName("isActivated")
    private boolean isActivated;

    // Getters
    public String getId() { return id; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getReferenceId() { return referenceId; }
    public int getOccurrenceNumber() { return occurrenceNumber; }
    public String getName() { return name; }
    public boolean isClientFault() { return isClientFault; }
    public boolean isSendToApp() { return sendToApp; }
    public boolean isActivated() { return isActivated; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public void setOccurrenceNumber(int occurrenceNumber) { this.occurrenceNumber = occurrenceNumber; }
    public void setName(String name) { this.name = name; }
    public void setClientFault(boolean clientFault) { isClientFault = clientFault; }
    public void setSendToApp(boolean sendToApp) { this.sendToApp = sendToApp; }
    public void setActivated(boolean activated) { isActivated = activated; }
}