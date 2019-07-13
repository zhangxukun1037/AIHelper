package com.android.aihelper.utils;

import android.text.TextUtils;
import android.util.Log;

import com.android.aihelper.common.Constant;

import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 2019/7/12 22:30
 */
public class RetrofitUtils {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    public static final String TAG = "RetrofitUtils_TAG";
    private static int timeout = 15;

    public static OkHttpClient getClient() {
        TimeUnit timeUnit = TimeUnit.SECONDS;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(timeout, timeUnit)
                .readTimeout(timeout, timeUnit)
                .writeTimeout(timeout, timeUnit)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request updateRequest = chain.request().newBuilder().build();
                        Response response = chain.proceed(updateRequest);
                        ResponseBody body = response.body();
                        String contentTypeStr = "";
                        String responseBodyStr = "";
                        if (body != null) {
                            BufferedSource source = body.source();
                            source.request(Long.MAX_VALUE);
                            Buffer buffer = source.buffer();
                            MediaType mediaType = body.contentType();
                            Charset charset = UTF8;
                            if (mediaType != null) {
                                try {
                                    contentTypeStr = mediaType.toString();
                                    charset = mediaType.charset(UTF8);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            responseBodyStr = buffer.clone().readString(charset);
                        }
                        if (!TextUtils.isEmpty(contentTypeStr) && contentTypeStr.contains("image")) {
                            responseBodyStr = contentTypeStr;
                        }
                        Log.i(TAG, "请求：" + response.request().url() + " \n返回结果：" + responseBodyStr);
                        return response;
                    }
                });
        return builder.build();
    }

    public static Retrofit defBuilder() {
        return new Retrofit.Builder().baseUrl(Constant.BASE_API)
                .client(getClient())
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
    }


    public static <T> T getServer(Class<T> service) {
        return defBuilder().create(service);
    }
}
