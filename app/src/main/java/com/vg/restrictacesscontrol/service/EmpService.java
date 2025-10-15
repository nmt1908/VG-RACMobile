package com.vg.restrictacesscontrol.service;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface EmpService {
    @GET("api/emp/getByEmpNoHRMMobile/{empno}")
    Call<ResponseBody> getByEmpNoHRM(@Path("empno") String empno);
}
