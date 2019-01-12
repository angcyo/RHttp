package com.angcyo.http;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.angcyo.http.log.HttpLoggingInterceptorM;
import com.angcyo.http.log.LogInterceptor;
import com.angcyo.http.log.LogUtil;
import com.angcyo.http.progress.ProgressIntercept;
import com.angcyo.http.type.TypeBuilder;
import okhttp3.*;
import retrofit2.Retrofit;
import retrofit2.RetrofitServiceMapping;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
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
        return builder(defaultOkHttpClick(logTag).build(), baseUrl);
    }

    public static Retrofit.Builder builder(OkHttpClient client, String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                ;
    }

    public static OkHttpClient.Builder defaultOkHttpClick(String logTag) {
        HttpLoggingInterceptorM httpLoggingInterceptorM = new HttpLoggingInterceptorM(new LogInterceptor(logTag));
        if (BuildConfig.DEBUG) {
            httpLoggingInterceptorM.setLevel(HttpLoggingInterceptorM.Level.BODY);
        }
        return new OkHttpClient.Builder()
                .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
                .readTimeout(TIME_OUT, TimeUnit.SECONDS)
                .writeTimeout(TIME_OUT, TimeUnit.SECONDS)
                .addNetworkInterceptor(httpLoggingInterceptorM)
                .addNetworkInterceptor(new ProgressIntercept());
    }

    public static <T> T create(Class<T> service) {
        return create(BASE_URL, service);
    }

    public static <T> T create(String baseUrl, Class<T> service) {
        return create(builder(baseUrl, "app->").build(), service);
    }

    /**
     * 创建接口
     */
    public static <T> T create(Retrofit retrofit, Class<T> service) {
        return RetrofitServiceMapping.mapping(retrofit, service).create(service);
    }

    /**
     * 默认调度转换
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

    public static <T> Observable.Transformer<ResponseBody, T> transformerBean(@NonNull final Type type) {
        return transformerBean(new IConvertJson<T>() {
            @Override
            public T covert(String body) {
                if (String.class.getName().contains(type.toString())) {
                    return (T) body;
                }
                return Json.from(body, type);
            }
        }, null);
    }

    public static <T> Observable.Transformer<ResponseBody, T> transformerBean(@NonNull final IConvertJson<T> convertJson,
                                                                              @Nullable final IConvertString convertString) {
        return new Observable.Transformer<ResponseBody, T>() {

            @Override
            public Observable<T> call(Observable<ResponseBody> responseObservable) {
                return responseObservable
                        .compose(Http.<ResponseBody>defaultTransformer())
                        .map(new Func1<ResponseBody, T>() {
                            @Override
                            public T call(ResponseBody stringResponse) {
                                String body = null;
                                try {
                                    body = stringResponse.string();

                                    //"接口返回数据-->\n" +
                                    LogUtil.json(TAG, body);

                                    if (convertString != null) {
                                        String covert = convertString.covert(body);
                                        if (TextUtils.equals(covert, body)) {
                                            LogUtil.i("IConvertString 转换前后一致");
                                        } else {
                                            LogUtil.json("转换后", covert);
                                        }
                                        body = covert;
                                    }

                                    return convertJson.covert(body);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    throw new HttpException(e, body);
                                }
                            }
                        });
            }
        };
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
                                            LogUtil.json("转换后", covert);
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
                                            LogUtil.json("转换后", covert);
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

    public interface IConvertJson<T> {
        T covert(String body);
    }

    public static String mapJson(String... args) {
        return Json.to(map(args));
    }

    /**
     * 组装参数
     */
    public static Map<String, Object> map(String... args) {
        final Map<String, Object> map = new HashMap<>();
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

            @Override
            public boolean isKeyAllowEmpty(String key) {
                return false;
            }

            @Override
            public void onEmptyValue(String key, String value) {
                map.put(key, "");
            }
        }, args);
        return map;
    }

    private static void foreach(@Nullable OnPutValue onPutValue, String... args) {
        if (onPutValue == null || args == null) {
            return;
        }
        for (String str : args) {
            if (TextUtils.isEmpty(str)) {
                continue;
            }

            int indexOf = str.indexOf(':');
            int length = str.length();
            if (indexOf != -1) {
                String key = str.substring(0, indexOf);

                if (indexOf == length) {
                    onPutValue.onRemove(key);
                } else {
                    String value = str.substring(indexOf + 1, length);
                    if (TextUtils.isEmpty(value)) {
                        if (onPutValue.isKeyAllowEmpty(key)) {
                            onPutValue.onEmptyValue(key, value);
                        } else {
                            onPutValue.onRemove(key);
                        }
                    } else {
                        onPutValue.onValue(key, str.substring(key.length() + 1));
                    }
                }
            }
        }
    }

    public static RequestBody getJsonBody(String json) {
        return RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json);
    }

    public static RequestBody getFileBody(String filePath) {
        return RequestBody.create(MultipartBody.FORM, new File(filePath));
    }

    public static RequestBody getMultipart(String filePath, String json) {
        return new MultipartBody.Builder()
                .addPart(getFileBody(filePath))
                .addPart(getJsonBody(json))
                .build();
    }

    public static RequestBody fileForm(String fileFormKey,
                                       String filePath,
                                       String... otherValues) {
        return fileForm(fileFormKey, filePath, null, otherValues);
    }

    /**
     * 表单形式上传文件, 和其他参数
     */
    public static RequestBody fileForm(String fileFormKey,
                                       String filePath,
                                       final List<String> emptyKeyList, //可以为空的key
                                       String... otherValues) {
        final MultipartBody.Builder builder = new MultipartBody.Builder();

        if (!TextUtils.isEmpty(filePath)) {
            File file = new File(filePath);
            if (file.exists()) {
                RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpg"), file);
                MultipartBody.Part fileBody = MultipartBody.Part.createFormData(fileFormKey, file.getName(), requestFile);
                builder.addPart(fileBody);
            }
        }

        foreach(new OnPutValue() {
            @Override
            public void onValue(String key, String value) {
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    builder.addFormDataPart(key, value);
                }
            }

            @Override
            public void onRemove(String key) {

            }

            @Override
            public boolean isKeyAllowEmpty(String key) {
                if (emptyKeyList != null) {
                    return emptyKeyList.contains(key);
                }
                return false;
            }

            @Override
            public void onEmptyValue(String key, String value) {
                builder.addFormDataPart(key, "");
            }
        }, otherValues);

        return builder.build();
    }

    /**
     * 简单的OkHttp网络请求
     */
    public static void request(@NonNull String url, @Nullable final OnHttpRequestCallback callback) {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                String body;
                if (responseBody == null || responseBody.contentLength() == 0) {
                    body = "";
                } else {
                    body = responseBody.string();
                }

                if (callback != null) {
                    callback.onRequestCallback(body);
                }
            }
        });
    }

    public interface OnHttpRequestCallback {
        void onRequestCallback(@NonNull String body);
    }

    interface OnPutValue {
        void onValue(String key, String value);

        void onRemove(String key);

        /**
         * key是否允许为空
         */
        boolean isKeyAllowEmpty(String key);

        /**
         * 当value为空时, 可以自行决定 设置 "" or null
         */
        void onEmptyValue(String key, String value);
    }
}
