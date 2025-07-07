package com.example.zylogi_motoristas;

import com.google.gson.annotations.SerializedName;

public class ClientAddress {
    @SerializedName("contact_name")
    private String contactName;

    @SerializedName("zip_code")
    private String zipCode;

    @SerializedName("address")
    private String address;

    @SerializedName("address_number")
    private String addressNumber;

    @SerializedName("city")
    private City city;

    @SerializedName("neighborhood")
    private Neighborhood neighborhood;

    // Getters
    public String getContactName() { return contactName; }
    public String getZipCode() { return zipCode; }
    public String getAddress() { return address; }
    public String getAddressNumber() { return addressNumber; }
    public City getCity() { return city; }
    public Neighborhood getNeighborhood() { return neighborhood; }
}