package com.angcyo.http;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Email:angcyo@126.com
 *
 * @author angcyo
 * @date 2019/01/17
 * Copyright (c) 2019 Shenzhen O&M Cloud Co., Ltd. All rights reserved.
 */
public class CopyrightInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder requestBuilder = original.newBuilder()
                .header("Copyright-Robi", "https://github.com/angcyo");
        Request request = requestBuilder.build();
        return chain.proceed(request);
    }
}
