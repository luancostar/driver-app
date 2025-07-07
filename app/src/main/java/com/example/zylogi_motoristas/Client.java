package com.example.zylogi_motoristas;

import com.google.gson.annotations.SerializedName;

public class Client {
    @SerializedName("company_name")
    private String companyName;

    @SerializedName("phone")
    private String phone;

    // Getters
    public String getCompanyName() { return companyName; }
    public String getPhone() { return phone; }
}