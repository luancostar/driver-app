package com.example.zylogi_motoristas;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final AuthSessionManager authSessionManager;

    public AuthInterceptor(Context context) {
        authSessionManager = new AuthSessionManager(context);
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request.Builder requestBuilder = chain.request().newBuilder();
        String token = authSessionManager.getAuthToken();

        Log.d("AuthInterceptor", "URL: " + chain.request().url());
        Log.d("AuthInterceptor", "Token disponível: " + (token != null ? "Sim" : "Não"));

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
            Log.d("AuthInterceptor", "Header Authorization adicionado");
        } else {
            Log.w("AuthInterceptor", "Nenhum token encontrado - requisição sem autenticação");
        }

        return chain.proceed(requestBuilder.build());
    }
}