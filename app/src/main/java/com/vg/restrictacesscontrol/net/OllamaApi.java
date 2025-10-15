// OllamaApi.java
package com.vg.restrictacesscontrol.net;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface OllamaApi {
    @Headers("Content-Type: application/json")
    @POST("api/generate")
    Call<OllamaResponse> generate(@Body OllamaRequest body);
}
