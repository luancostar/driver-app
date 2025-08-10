package com.example.zylogi_motoristas;// Local: app/src/main/java/com/example/zylogi_motoristas/ApiService.java

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import java.util.List;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.PATCH;
import retrofit2.http.Path;

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

    @FormUrlEncoded
    @PATCH("pickups/{id}/driver-finalize")
    Call<Pickup> finalizePickup(
            @Path("id") String pickupId,
            @Field("status") String status
    );

    @FormUrlEncoded
    @PATCH("pickups/{id}/driver-finalize")
    Call<Pickup> finalizePickupBasic(
            @Path("id") String pickupId,
            @Field("status") String status,
            @Field("observationDriver") String observationDriver,
            @Field("occurrenceId") String occurrenceId,
            @Field("driverAttachmentUrl") String driverAttachmentUrl,
            @Field("driverId") String driverId
    );

    @FormUrlEncoded
    @PATCH("pickups/{id}/driver-finalize")
    Call<Pickup> finalizePickupWithDetails(
            @Path("id") String pickupId,
            @Field("status") String status,
            @Field("observationDriver") String observationDriver,
            @Field("occurrenceId") String occurrenceId,
            @Field("driverAttachmentUrl") String driverAttachmentUrl,
            @Field("driverId") String driverId,
            @Field("vehicleId") String vehicleId,
            @Field("pickupRouteId") String pickupRouteId
    );

}