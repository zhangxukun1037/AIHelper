package com.android.aihelper.common;

import io.reactivex.Observable;
import okhttp3.ResponseBody;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 2019/7/13 14:35
 */
public interface ApiService {
    @FormUrlEncoded
    @POST("http://openapi.tuling123.com/openapi/api/v2")
    Observable<ResponseBody> chatNetWorkAI(@Field("perception") String perception);


    @GET("http://api.qingyunke.com/api.php?key=free&appid=0")
    Observable<ResponseBody> qingyunkeChat(@Query("msg") String msg);

    @GET("http://v.juhe.cn/weather/index?format=2&key=1ec8b737f05c652c7972778167955066")
    Observable<ResponseBody> getWeather(@Query("cityname") String cityname);
}
