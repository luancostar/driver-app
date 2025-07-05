package com.example.zylogi_motoristas;// Local: app/src/main/java/com/example/zylogi_motoristas/ApiService.java

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("auth/driver/login") // Este é o endpoint exato da sua API
    Call<LoginResponse> login(@Body LoginRequest loginRequest);

}