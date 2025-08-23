package com.example.zylogi_motoristas;// Local: app/src/main/java/com/example/zylogi_motoristas/ApiService.java

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import java.util.List;
import java.util.Map;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public interface ApiService {

    @POST("auth/driver/login") // Este é o endpoint exato da sua API
    Call<LoginResponse> login(@Body LoginRequest loginRequest);

    @GET("pickups")
    Call<List<Pickup>> getPickups(
            @Query("driverId") String driverId,
            @Query("startDate") String startDate,
            @Query("endDate") String endDate
    );

    @GET("occurrences/driver")
    Call<List<Occurrence>> getDriverOccurrences();

    @GET("pickups/{id}")
    Call<Pickup> getPickupById(@Path("id") String pickupId);

    // Método para finalizar coletas - usa Map para enviar apenas campos necessários
    @PATCH("pickups/{id}/driver-finalize")
    Call<Pickup> finalizePickup(
            @Path("id") String pickupId,
            @Body Map<String, Object> updates
    );

    // Método para finalizar coletas com foto usando multipart/form-data
    @Multipart
    @PATCH("pickups/{id}/driver-finalize")
    Call<Pickup> finalizePickupWithPhoto(
            @Path("id") String pickupId,
            @Part("status") RequestBody status,
            @Part("observationDriver") RequestBody observationDriver,
            @Part("occurrenceId") RequestBody occurrenceId,
            @Part("completionDate") RequestBody completionDate,
            @Part("driverNumberPackages") RequestBody driverNumberPackages,
            @Part MultipartBody.Part driverAttachmentUrl
    );
}