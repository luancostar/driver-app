package com.example.zylogi_motoristas;

import android.content.Context;
import com.example.zylogi_motoristas.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.Interceptor;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor; // Importe
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.IOException;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    public static Retrofit getClient(Context context) {
        // Só recria se for nulo, para não adicionar interceptors duplicados
        if (retrofit == null) {
            try {
                // Configurar interceptor de logging detalhado
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                android.util.Log.d("OkHttp", message);
            }
        });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        // Interceptor personalizado para logs de multipart
        Interceptor multipartInterceptor = new Interceptor() {
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                okhttp3.Request request = chain.request();
                
                if (request.body() instanceof MultipartBody) {
                    android.util.Log.d("RetrofitClient", "=== MULTIPART REQUEST DETECTED ===");
                    MultipartBody multipartBody = (MultipartBody) request.body();
                    android.util.Log.d("RetrofitClient", "Multipart parts count: " + multipartBody.parts().size());
                    
                    for (int i = 0; i < multipartBody.parts().size(); i++) {
                        MultipartBody.Part part = multipartBody.parts().get(i);
                        android.util.Log.d("RetrofitClient", "Part " + i + ": " + part.headers());
                    }
                }
                
                return chain.proceed(request);
            }
        }; // Mostra tudo: URL, headers, body

                OkHttpClient okHttpClient = new OkHttpClient.Builder()
                        .addInterceptor(new AuthInterceptor(context))
                        .addInterceptor(multipartInterceptor)
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