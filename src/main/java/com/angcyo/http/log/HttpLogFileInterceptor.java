package com.angcyo.http.log;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.angcyo.http.progress.ProgressIntercept;
import okhttp3.*;
import okhttp3.internal.http.HttpHeaders;
import okio.Buffer;
import okio.BufferedSource;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 将Http请求写入文件日志
 * <p>
 * Email:angcyo@126.com
 *
 * @author angcyo
 * @date 2018/12/17
 */
public class HttpLogFileInterceptor implements Interceptor {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    OnHttpLogIntercept onHttpLogIntercept;

    public HttpLogFileInterceptor(OnHttpLogIntercept onHttpLogIntercept) {
        this.onHttpLogIntercept = onHttpLogIntercept;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (onHttpLogIntercept == null) {
            return chain.proceed(request);
        }

        //创建一个请求的唯一标识符
        String uuid = UUID.randomUUID().toString().replace("-", "");

        //请求数据
        StringBuilder requestBuilder = new StringBuilder("Request->");
        requestBuilder.append(uuid);
        requestBuilder.append("\n");
        //返回数据
        StringBuilder responseBuilder = new StringBuilder("Response->");
        responseBuilder.append(uuid);
        responseBuilder.append("\n");

        /*
            打印开始开始请求
         */
        RequestBody requestBody = request.body();
        boolean hasRequestBody = requestBody != null;

        Connection connection = chain.connection();
        Protocol protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
        //请求方法,url和协议
        requestBuilder.append(request.method());
        requestBuilder.append(" ");
        requestBuilder.append(request.url());
        requestBuilder.append(" ");
        requestBuilder.append(protocol);
        requestBuilder.append("\n");

        /*打印请求体*/
        Headers headers = request.headers();
        for (int i = 0, count = headers.size(); i < count; i++) {
            String name = headers.name(i);
            // Skip headers from the request body as they are explicitly logged above.
            if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                requestBuilder.append(name);
                requestBuilder.append(": ");
                requestBuilder.append(headers.value(i));
                requestBuilder.append("\n");
            }
        }

        /*打印Content-Type*/
        if (hasRequestBody) {
            String simpleName = requestBody.getClass().getSimpleName();
            if (!TextUtils.isEmpty(simpleName)) {
                requestBuilder.append("Body-Class:").append(simpleName).append("\n");
            }

            // Request body headers are only present when installed as a network interceptor. Force
            // them to be included (when available) so there values are known.
            if (requestBody.contentType() != null) {
                requestBuilder.append("Content-Type: ");
                requestBuilder.append(requestBody.contentType());
                requestBuilder.append("\n");
            }
            if (requestBody.contentLength() != -1) {
                requestBuilder.append("Content-Length: ");
                requestBuilder.append(requestBody.contentLength());
            }
            requestBuilder.append("\n");

            /*请求打印结束*/
            if (bodyEncoded(request.headers())) {
                requestBuilder.append("(encoded body omitted)");
            } else if (requestBody instanceof MultipartBody) {
                Buffer buffer = new Buffer();
                for (MultipartBody.Part part : ((MultipartBody) requestBody).parts()) {
                    RequestBody body = part.body();
                    Headers partBodyHeaders = part.headers();

                    if (partBodyHeaders != null) {
                        requestBuilder.append(partBodyHeaders);
                    }

                    if (body.contentType() == null) {
                        buffer.clear();
                        Charset charset = UTF8;
                        //字符串键值对
                        body.writeTo(buffer);

                        requestBuilder.append(buffer.readString(charset)).append("\n");
                    } else {
                        try {
                            requestBuilder.append(body.contentType().toString()).append(" ");
                            requestBuilder.append(ProgressIntercept.formatSize(body.contentLength())).append("\n");

                            Field[] fields = part.body().getClass().getDeclaredFields();
                            for (Field f : fields) {
                                if (f.getName().contains("file")) {
                                    f.setAccessible(true);
                                    File file = (File) f.get(part.body());
                                    requestBuilder.append(file.getAbsolutePath()).append("\n");
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                Buffer buffer = new Buffer();
                requestBody.writeTo(buffer);

                Charset charset = UTF8;
                MediaType contentType = requestBody.contentType();
                if (contentType != null) {
                    charset = contentType.charset(UTF8);
                }

                if (isPlaintext(buffer)) {
                    requestBuilder.append(buffer.readString(charset));
                } else {
                    requestBuilder.append("(binary "
                            + requestBody.contentLength()
                            + "-byte body omitted)");
                }
            }
        } else {
            requestBuilder.append("No Request Body.");
        }

        try {
            if (onHttpLogIntercept != null) {
                onHttpLogIntercept.onHttpRequestLogIntercept(requestBuilder.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         *  返回数据打印
         */
        long startNs = System.nanoTime();
        Response response;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            responseBuilder.append("请求失败: ");
            responseBuilder.append(e);
            responseBuilder.append("\n");

            try {
                if (onHttpLogIntercept != null) {
                    onHttpLogIntercept.onHttpResponseLogIntercept(responseBuilder.toString());
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            throw e;
        }

        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        /*
            请求完后打印RequestBody
         */

        /* 请求完后打印ResponseBody,  打印返回体 */
        ResponseBody responseBody = response.body();
        long contentLength = responseBody.contentLength();
        String bodySize = contentLength != -1 ? contentLength + "-byte" : "unknown-length";

        responseBuilder.append(response.code());
        responseBuilder.append(" ");
        responseBuilder.append(response.message());
        responseBuilder.append(" (" + tookMs + "ms " + bodySize + ')');

        if (!HttpHeaders.hasBody(response)) {
            responseBuilder.append("No Response Body.");
        } else if (bodyEncoded(response.headers())) {
            responseBuilder.append("(encoded body omitted)");
        } else {
            BufferedSource source = responseBody.source();
            // Buffer the entire body.
            source.request(Long.MAX_VALUE);
            Buffer buffer = source.buffer();

            Charset charset = UTF8;
            MediaType contentType = responseBody.contentType();
            if (contentType != null) {
                try {
                    charset = contentType.charset(UTF8);
                } catch (UnsupportedCharsetException e) {

                    responseBuilder.append("Couldn't decode the response body; charset is likely malformed.");
                    responseBuilder.append("\n");

                    try {
                        if (onHttpLogIntercept != null) {
                            onHttpLogIntercept.onHttpResponseLogIntercept(responseBuilder.toString());
                        }
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                    return response;
                }
            }

            responseBuilder.append("\n");
            if (!isPlaintext(buffer)) {
                responseBuilder.append("(binary " + buffer.size() + "-byte body omitted)");
                responseBuilder.append("\n");

                try {
                    if (onHttpLogIntercept != null) {
                        onHttpLogIntercept.onHttpResponseLogIntercept(responseBuilder.toString());
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

                return response;
            }

            if (contentLength != 0) {
                responseBuilder.append(buffer.clone()
                        .readString(charset));

            }
            responseBuilder.append("\n");
            responseBuilder.append("(" + buffer.size() + "-byte body)");
        }

        try {
            if (onHttpLogIntercept != null) {
                onHttpLogIntercept.onHttpResponseLogIntercept(responseBuilder.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
    }

    static boolean isPlaintext(Buffer buffer) throws EOFException {
        try {
            Buffer prefix = new Buffer();
            long byteCount = buffer.size() < 64 ? buffer.size() : 64;
            buffer.copyTo(prefix, 0, byteCount);
            for (int i = 0; i < 16; i++) {
                if (prefix.exhausted()) {
                    break;
                }
                int codePoint = prefix.readUtf8CodePoint();
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false;
                }
            }
            return true;
        } catch (EOFException e) {
            return false; // Truncated UTF-8 sequence.
        }
    }

    private boolean bodyEncoded(Headers headers) {
        String contentEncoding = headers.get("Content-Encoding");
        return contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity");
    }

    public interface OnHttpLogIntercept {
        void onHttpRequestLogIntercept(@NonNull String requestString);

        void onHttpResponseLogIntercept(@NonNull String responseString);
    }

}
