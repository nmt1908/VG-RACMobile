package com.vg.restrictacesscontrol.service;

import com.vg.restrictacesscontrol.models.Car;

import java.util.List;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("vg-bta/getCarList")
    Call<List<Car>> getCarList();
    @POST("access-control/saveDataInAndOutOfGateBTANew")
    Call<ResponseBody> saveDataInOutOfGateBTANew(@Body RequestBody body);

    @POST("vg-bta/confirmInfoMulTrips")
    Call<ResponseBody> confirmInfoMulTrips(@Body RequestBody body);
}
