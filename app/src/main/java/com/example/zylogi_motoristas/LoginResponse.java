package com.example.zylogi_motoristas;// Local: app/src/main/java/com/example/zylogi_motoristas/LoginResponse.java

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    // A anotação @SerializedName garante que o nome do campo no JSON
    // seja mapeado corretamente para esta variável.
    @SerializedName("access_token")
    private String accessToken;

    public String getAccessToken() {
        return accessToken;
    }
}