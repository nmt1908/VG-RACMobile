package com.vg.restrictacesscontrol.service;

import com.vg.restrictacesscontrol.models.BusinessTripResponse;
import com.vg.restrictacesscontrol.models.CheckReq;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface VgBtaService {
    @Headers("Content-Type: application/json")
    @POST("api/vg-bta/checkQrCodeV3")
    Call<BusinessTripResponse> checkQrCodeV3(@Body CheckReq body);
}
