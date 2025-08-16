package com.example.zylogi_motoristas;

import android.content.Context;
import com.example.zylogi_motoristas.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor; // Importe
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    public static Retrofit getClient(Context context) {
        // Só recria se for nulo, para não adicionar interceptors duplicados
        if (retrofit == null) {
            try {
                // Cria o interceptor de logging
                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // Mostra tudo: URL, headers, body

                OkHttpClient okHttpClient = new OkHttpClient.Builder()
                        .addInterceptor(new AuthInterceptor(context))
                        .addInterceptor(loggingInterceptor) // Adiciona o espião aqui
                        .build();

                // Configura Gson para não incluir campos vazios ou nulos
                Gson gson = new GsonBuilder()
                        .setLenient()
                        .create();

                retrofit = new Retrofit.Builder()
                        .baseUrl(BuildConfig.API_BASE_URL)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create(gson))
                        .build();
                        
                android.util.Log.d("RetrofitClient", "Cliente Retrofit criado com sucesso para: " + BuildConfig.API_BASE_URL);
                
            } catch (Exception e) {
                android.util.Log.e("RetrofitClient", "Erro ao criar cliente Retrofit", e);
                throw new RuntimeException("Falha ao configurar cliente de rede", e);
            }
        }
        return retrofit;
    }
}