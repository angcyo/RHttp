package com.angcyo.http;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.angcyo.http.log.HttpLoggingInterceptorM;
import com.angcyo.http.log.LogInterceptor;
import com.angcyo.http.log.LogUtil;
import com.angcyo.http.progress.ProgressIntercept;
import com.angcyo.http.type.TypeBuilder;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public static final String TAG = "HttpResult";

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
    public static <T> Observable.Transformer<ResponseBody, T> transformerBean(@NonNull final Class<T> type) {
        return transformerBean(type, null);
    }

    public static <T> Observable.Transformer<ResponseBody, T> transformerBean(@NonNull final Class<T> type,
                                                                              @Nullable final IConvertString convert) {
        return new Observable.Transformer<ResponseBody, T>() {

            @Override
            public Observable<T> call(Observable<ResponseBody> responseObservable) {
                return responseObservable
                        .compose(Http.<ResponseBody>defaultTransformer())
                        .map(new Func1<ResponseBody, T>() {
                            @Override
                            public T call(ResponseBody stringResponse) {
                                T bean;
                                String body = null;
                                try {
                                    body = stringResponse.string();

                                    //"接口返回数据-->\n" +
                                    LogUtil.json(TAG, body);

                                    if (convert != null) {
                                        String covert = convert.covert(body);
                                        if (TextUtils.equals(covert, body)) {
                                            LogUtil.i("IConvertString 转换前后一致");
                                        } else {
                                            LogUtil.json("转换后", body);
                                        }
                                        body = covert;
                                    }

                                    if (type.isAssignableFrom(String.class)) {
                                        return (T) body;
                                    }

                                    bean = Json.from(body, type);
                                    return bean;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    throw new HttpException(e, body);
                                }
                            }
                        });
            }
        };
    }

    /**
     * ResponseBody->List<T> 转换
     */
    public static <T> Observable.Transformer<ResponseBody, List<T>> transformerListBean(@NonNull final Class<T> type,
                                                                                        @Nullable final IConvertString convert) {
        return new Observable.Transformer<ResponseBody, List<T>>() {

            @Override
            public Observable<List<T>> call(Observable<ResponseBody> responseObservable) {
                return responseObservable
                        .compose(Http.<ResponseBody>defaultTransformer())
                        .map(new Func1<ResponseBody, List<T>>() {
                            @Override
                            public List<T> call(ResponseBody stringResponse) {
                                List<T> list;
                                String body = null;
                                try {
                                    body = stringResponse.string();

                                    //"接口返回数据-->\n" +
                                    LogUtil.json(TAG, body);

                                    if (convert != null) {
                                        String covert = convert.covert(body);
                                        if (TextUtils.equals(covert, body)) {
                                            LogUtil.i("IConvertString 转换前后一致");
                                        } else {
                                            LogUtil.json("转换后", body);
                                        }
                                        body = covert;
                                    }

                                    list = Json.from(body, TypeBuilder.newInstance(List.class).addTypeParam(type).build());
                                    return list;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    throw new HttpException(e, body);
                                }
                            }
                        });
            }
        };
    }

    public static <T> Observable.Transformer<ResponseBody, List<T>> transformerListBean(@NonNull final Class<T> type) {
        return transformerListBean(type, null);
    }

    public interface IConvertString {
        String covert(String body);
    }

    public static String mapJson(String... args) {
        return Json.to(map(args));
    }

    /**
     * 组装参数
     */
    public static Map<String, String> map(String... args) {
        final Map<String, String> map = new HashMap<>();
        foreach(new OnPutValue() {
            @Override
            public void onValue(String key, String value) {
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    map.put(key, value);
                }
            }

            @Override
            public void onRemove(String key) {
                map.remove(key);
            }
        }, args);
        return map;
    }

    private static void foreach(OnPutValue onPutValue, String... args) {
        if (onPutValue == null || args == null) {
            return;
        }
        for (String str : args) {
            String[] split = str.split(":");
            if (split.length >= 2) {
                String first = split[0];
                if (TextUtils.isEmpty(split[1])) {
                    onPutValue.onRemove(split[0]);
                } else {
                    onPutValue.onValue(first, str.substring(first.length() + 1));
                }
            } else if (split.length == 1) {
                onPutValue.onRemove(split[0]);
            }
        }
    }

    interface OnPutValue {
        void onValue(String key, String value);

        void onRemove(String key);
    }
}
