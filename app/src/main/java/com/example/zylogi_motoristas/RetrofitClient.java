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
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.net.ConnectException;
import okhttp3.Request;
import okhttp3.Dns;
import java.util.List;
import java.util.Arrays;

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
        
        // DNS customizado com fallback para servidores públicos
        Dns customDns = new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                try {
                    // Tenta resolver normalmente primeiro
                    return Dns.SYSTEM.lookup(hostname);
                } catch (UnknownHostException e) {
                    android.util.Log.w("RetrofitClient", "System DNS failed for " + hostname + ", trying public DNS");
                    
                    // Se falhar, tenta com DNS público (Google)
                    try {
                        System.setProperty("dns.server", "8.8.8.8");
                        return Arrays.asList(InetAddress.getByName(hostname));
                    } catch (Exception e2) {
                        android.util.Log.w("RetrofitClient", "Public DNS also failed for " + hostname);
                        
                        // Como último recurso, retorna o IP conhecido
                        if (hostname.equals("api.homolog.zylogi.com")) {
                            return Arrays.asList(InetAddress.getByName("82.29.57.60"));
                        }
                        throw new UnknownHostException("Unable to resolve " + hostname);
                    }
                }
            }
        };
        
        // Interceptor de fallback melhorado
        Interceptor fallbackInterceptor = new Interceptor() {
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                okhttp3.Request request = chain.request();
                
                try {
                    // Tenta a requisição original primeiro
                    return chain.proceed(request);
                } catch (UnknownHostException | ConnectException e) {
                    android.util.Log.w("RetrofitClient", "Connection failed: " + e.getMessage());
                    
                    // Se for problema de DNS ou conexão, tenta estratégias alternativas
                    String originalUrl = request.url().toString();
                    
                    // Estratégia 1: Tentar com hostname original mas forçando IPv4
                    if (e instanceof UnknownHostException) {
                        try {
                            android.util.Log.i("RetrofitClient", "Trying with forced IPv4 resolution");
                            System.setProperty("java.net.preferIPv4Stack", "true");
                            return chain.proceed(request);
                        } catch (Exception e2) {
                            android.util.Log.w("RetrofitClient", "IPv4 strategy failed: " + e2.getMessage());
                        }
                    }
                    
                    // Estratégia 2: Usar HTTP se HTTPS falhar (apenas para desenvolvimento)
                    if (originalUrl.startsWith("https://") && originalUrl.contains("api.homolog.zylogi.com")) {
                        try {
                            String httpUrl = originalUrl.replace("https://", "http://");
                            android.util.Log.i("RetrofitClient", "Trying HTTP fallback: " + httpUrl);
                            
                            Request httpRequest = request.newBuilder()
                                    .url(httpUrl)
                                    .build();
                            
                            return chain.proceed(httpRequest);
                        } catch (Exception e3) {
                            android.util.Log.w("RetrofitClient", "HTTP fallback failed: " + e3.getMessage());
                        }
                    }
                    
                    // Se todas as estratégias falharam, relança a exceção original
                    throw e;
                }
            }
        };
        
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
                        .dns(customDns) // Adiciona DNS customizado
                        .addInterceptor(fallbackInterceptor) // Adiciona fallback primeiro
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