package com.angcyo.http;

import com.angcyo.http.log.HttpLoggingInterceptorM;
import com.angcyo.http.log.LogInterceptor;
import com.angcyo.http.log.LogUtil;
import com.angcyo.http.progress.ProgressIntercept;
import com.angcyo.http.type.TypeBuilder;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Email:angcyo@126.com
 *
 * @author angcyo
 * @date 2018/10/15
 */
public class Http {
    public static int TIME_OUT = 5_000;
    public static String BASE_URL = "http://www.api.com";

    public static Retrofit.Builder builder(String baseUrl, String logTag) {
        HttpLoggingInterceptorM httpLoggingInterceptorM = new HttpLoggingInterceptorM(new LogInterceptor(logTag));
        if (BuildConfig.DEBUG) {
            httpLoggingInterceptorM.setLevel(HttpLoggingInterceptorM.Level.BODY);
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
                .readTimeout(TIME_OUT, TimeUnit.SECONDS)
                .writeTimeout(TIME_OUT, TimeUnit.SECONDS)
                .addNetworkInterceptor(httpLoggingInterceptorM)
                .addNetworkInterceptor(new ProgressIntercept())
                .build();

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                ;
    }


    public static <T> T create(String baseUrl, Class<T> service) {
        return create(builder(baseUrl, "app->").build(), service);
    }

    public static <T> T create(Retrofit retrofit, Class<T> service) {
        return retrofit.create(service);
    }

    public static <T> T create(Class<T> service) {
        return create(BASE_URL, service);
    }

    /**
     * 现成调度转换
     */
    public static <T> Observable.Transformer<T, T> defaultTransformer() {
        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> tObservable) {
                return tObservable.unsubscribeOn(Schedulers.io())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread());
            }
        };
    }

    /**
     * ResponseBody->字符串 转换
     */
    public static Observable.Transformer<ResponseBody, String> transformerString() {
        return transformerBean(String.class);
    }

    /**
     * ResponseBody->T 转换
     */
    public static <T> Observable.Transformer<ResponseBody, T> transformerBean(final Class<T> type) {
        return new Observable.Transformer<ResponseBody, T>() {

            @Override
            public Observable<T> call(Observable<ResponseBody> responseObservable) {
                return responseObservable
                        .compose(Http.<ResponseBody>defaultTransformer())
                        .map(new Func1<ResponseBody, T>() {
                            @Override
                            public T call(ResponseBody stringResponse) {
                                T bean;
                                String body;
                                try {
                                    body = stringResponse.string();

                                    //"接口返回数据-->\n" +
                                    LogUtil.json(body);

                                    if (type.isAssignableFrom(String.class)) {
                                        return (T) body;
                                    }

                                    bean = Json.from(body, type);
                                    return bean;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    //throw new RException(-1000, "服务器数据异常.", e.getMessage());
                                }
                                //throw new NullPointerException("无数据.");
                                return null;
                            }
                        });
            }
        };
    }

    /**
     * ResponseBody->List<T> 转换
     */
    public static <T> Observable.Transformer<ResponseBody, List<T>> transformerListBean(final Class<T> type) {
        return new Observable.Transformer<ResponseBody, List<T>>() {

            @Override
            public Observable<List<T>> call(Observable<ResponseBody> responseObservable) {
                return responseObservable
                        .compose(Http.<ResponseBody>defaultTransformer())
                        .map(new Func1<ResponseBody, List<T>>() {
                            @Override
                            public List<T> call(ResponseBody stringResponse) {
                                List<T> list = new ArrayList<>();
                                String body;
                                try {
                                    body = stringResponse.string();

                                    //"接口返回数据-->\n" +
                                    LogUtil.json(body);

                                    list = Json.from(body, TypeBuilder.newInstance(List.class).addTypeParam(type).build());
                                    return list;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    //throw new RException(-1000, "服务器数据异常.", e.getMessage());
                                }
                                //throw new NullPointerException("无数据.");
                                return list;
                            }
                        })
                        ;
            }
        };
    }

}
