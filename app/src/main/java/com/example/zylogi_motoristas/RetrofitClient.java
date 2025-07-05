package com.example.zylogi_motoristas;// Local: app/src/main/java/com/example/zylogi_motoristas/RetrofitClient.java

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL) // Usando a variável do build.gradle!
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}