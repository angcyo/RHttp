package com.angcyo.http.progress;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static com.angcyo.http.progress.ProgressManager.*;

/**
 * Email:angcyo@126.com
 *
 * @author angcyo
 * @date 2018/10/15
 */
public class ProgressIntercept implements Interceptor {
    private final Handler mHandler;
    private static final Map<String, List<ProgressListener>> mResponseListeners = new WeakHashMap<>();
    private static final Map<String, List<ProgressListener>> mRequestListeners = new WeakHashMap<>();
    private int mRefreshTime = DEFAULT_REFRESH_TIME; //进度刷新时间(单位ms),避免高频率调用

    /**
     * 将需要被监听上传进度的 {@code url} 注册到管理器,此操作请在页面初始化时进行,切勿多次注册同一个(内容相同)监听器
     *
     * @param url      {@code url} 作为标识符
     * @param listener 当此 {@code url} 地址存在上传的动作时,此监听器将被调用
     */
    public static void addRequestListener(String url, ProgressListener listener) {
        checkNotNull(url, "url cannot be null");
        checkNotNull(listener, "listener cannot be null");
        List<ProgressListener> progressListeners;
        synchronized (ProgressManager.class) {
            progressListeners = mRequestListeners.get(url);
            if (progressListeners == null) {
                progressListeners = new LinkedList<>();
                mRequestListeners.put(url, progressListeners);
            }
        }
        progressListeners.add(listener);
    }

    public static void removeRequestListener(String url, ProgressListener listener) {
        checkNotNull(url, "url cannot be null");
        checkNotNull(listener, "listener cannot be null");
        List<ProgressListener> progressListeners;
        synchronized (ProgressManager.class) {
            progressListeners = mRequestListeners.get(url);
            if (progressListeners != null) {
                progressListeners.remove(listener);
            }
        }
    }

    /**
     * 将需要被监听下载进度的 {@code url} 注册到管理器,此操作请在页面初始化时进行,切勿多次注册同一个(内容相同)监听器
     *
     * @param url      {@code url} 作为标识符
     * @param listener 当此 {@code url} 地址存在下载的动作时,此监听器将被调用
     */
    public static void addResponseListener(String url, ProgressListener listener) {
        checkNotNull(url, "url cannot be null");
        checkNotNull(listener, "listener cannot be null");
        List<ProgressListener> progressListeners;
        synchronized (ProgressManager.class) {
            progressListeners = mResponseListeners.get(url);
            if (progressListeners == null) {
                progressListeners = new LinkedList<>();
                mResponseListeners.put(url, progressListeners);
            }
        }
        progressListeners.add(listener);
    }

    public static void removeResponseListener(String url, ProgressListener listener) {
        checkNotNull(url, "url cannot be null");
        checkNotNull(listener, "listener cannot be null");
        List<ProgressListener> progressListeners;
        synchronized (ProgressManager.class) {
            progressListeners = mResponseListeners.get(url);
            if (progressListeners != null) {
                progressListeners.remove(listener);
            }
        }
    }

    public static String formatSize(long byteNum) {
        return ConvertUtils.byte2FitMemorySize(byteNum);
    }

    public ProgressIntercept() {
        this.mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        return wrapResponseBody(chain.proceed(wrapRequestBody(chain.request())));
    }

    public Request wrapRequestBody(Request request) {
        if (request == null)
            return request;

        String key = request.url().toString();
        request = pruneIdentification(key, request);

        if (request.body() == null)
            return request;
//        if (mRequestListeners.containsKey(key)) {
        List<ProgressListener> listeners = mRequestListeners.get(key);
        if (listeners == null) {
            listeners = new LinkedList<>();
            mRequestListeners.put(key, listeners);
        }
        return request.newBuilder()
                .method(request.method(), new ProgressRequestBody(mHandler, request.body(), listeners, mRefreshTime))
                .build();
//        }
//        return request;
    }

    public Response wrapResponseBody(Response response) {
        if (response == null)
            return response;

        String key = response.request().url().toString();
        if (!TextUtils.isEmpty(response.request().header(IDENTIFICATION_HEADER))) { //从 header 中拿出有标识符的 url
            key = response.request().header(IDENTIFICATION_HEADER);
        }

        if (response.isRedirect()) {
            resolveRedirect(mRequestListeners, response, key);
            String location = resolveRedirect(mResponseListeners, response, key);
            response = modifyLocation(response, location);
            return response;
        }

        if (response.body() == null)
            return response;

//        if (mResponseListeners.containsKey(key)) {
        List<ProgressListener> listeners = mResponseListeners.get(key);
        if (listeners == null) {
            listeners = new LinkedList<>();
            mResponseListeners.put(key, listeners);
        }
        return response.newBuilder()
                .body(new ProgressResponseBody(mHandler, response.body(), listeners, mRefreshTime))
                .build();
//        }
//        return response;
    }

    private Request pruneIdentification(String url, Request request) {
        boolean needPrune = url.contains(IDENTIFICATION_NUMBER);
        if (!needPrune)
            return request;
        return request.newBuilder()
                .url(url.substring(0, url.indexOf(IDENTIFICATION_NUMBER))) //删除掉标识符
                .header(IDENTIFICATION_HEADER, url) //将有标识符的 url 加入 header中, 便于wrapResponseBody(Response) 做处理
                .build();
    }

    private String resolveRedirect(Map<String, List<ProgressListener>> map, Response response, String url) {
        String location = null;
        List<ProgressListener> progressListeners = map.get(url); //查看此重定向 url ,是否已经注册过监听器
        if (progressListeners != null && progressListeners.size() > 0) {
            location = response.header(LOCATION_HEADER);// 重定向地址
            if (!TextUtils.isEmpty(location)) {
                if (url.contains(IDENTIFICATION_NUMBER) && !location.contains(IDENTIFICATION_NUMBER)) { //如果 url 有标识符,那也将标识符加入用于重定向的 location
                    location += url.substring(url.indexOf(IDENTIFICATION_NUMBER), url.length());
                }
                if (!map.containsKey(location)) {
                    map.put(location, progressListeners); //将需要重定向地址的监听器,提供给重定向地址,保证重定向后也可以监听进度
                } else {
                    List<ProgressListener> locationListener = map.get(location);
                    for (ProgressListener listener : progressListeners) {
                        if (!locationListener.contains(listener)) {
                            locationListener.add(listener);
                        }
                    }
                }
            }
        }
        return location;
    }

    private Response modifyLocation(Response response, String location) {
        if (!TextUtils.isEmpty(location) && location.contains(IDENTIFICATION_NUMBER)) { //将被加入标识符的新的 location 放入 header 中
            response = response.newBuilder()
                    .header(LOCATION_HEADER, location)
                    .build();
        }
        return response;
    }

}
