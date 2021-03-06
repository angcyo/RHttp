package com.angcyo.http.log;

import android.text.TextUtils;
import com.angcyo.http.progress.ProgressIntercept;
import okhttp3.*;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.platform.Platform;
import okio.Buffer;
import okio.BufferedSource;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.concurrent.TimeUnit;

import static okhttp3.internal.platform.Platform.INFO;

/**
 * author: baiiu
 * date: on 16/8/31 19:09
 * description: https://github.com/baiiu/LogUtil
 * 2018-10-15
 */
public final class HttpLoggingInterceptorM implements Interceptor {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public enum Level {
        /**
         * No logs.
         */
        NONE,
        /**
         * Logs request and response lines.
         *
         * <p>Example:
         * <pre>{@code
         * --> POST /greeting http/1.1 (3-byte body)
         *
         * <-- 200 OK (22ms, 6-byte body)
         * }</pre>
         */
        BASIC,
        /**
         * Logs request and response lines and their respective headers.
         *
         * <p>Example:
         * <pre>{@code
         * --> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         * --> END POST
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         * <-- END HTTP
         * }</pre>
         */
        HEADERS,
        /**
         * Logs request and response lines and their respective headers and bodies (if present).
         *
         * <p>Example:
         * <pre>{@code
         * --> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         *
         * Hi?
         * --> END GET
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         *
         * Hello!
         * <-- END HTTP
         * }</pre>
         */
        BODY
    }

    public interface Logger {
        void log(String message, @LogUtil.LogType int type);

        /**
         * A {@link Logger} defaults output appropriate for the current platform.
         */
        Logger DEFAULT = new Logger() {
            @Override
            public void log(String message, @LogUtil.LogType int type) {
                Platform.get()
                        .log(INFO, message, null);
            }
        };
    }

    public HttpLoggingInterceptorM() {
        this(Logger.DEFAULT);
    }

    public HttpLoggingInterceptorM(Logger logger) {
        this.logger = logger;
    }

    private final Logger logger;

    /**
     * 是打印返回体数据的log
     */
    public boolean logResponse = true;

    private volatile Level level = Level.NONE;

    /**
     * Change the level at which this interceptor logs.
     */
    public HttpLoggingInterceptorM setLevel(Level level) {
        if (level == null) throw new NullPointerException("level == null. Use Level.NONE instead.");
        this.level = level;
        return this;
    }

    public Level getLevel() {
        return level;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Level level = this.level;

        Request request = chain.request();
        if (level == Level.NONE) {
            return chain.proceed(request);
        }

        boolean logBody = level == Level.BODY;
        boolean logHeaders = logBody || level == Level.HEADERS;

        /*
            打印开始开始请求
         */
        RequestBody requestBody = request.body();
        boolean hasRequestBody = requestBody != null;

        Connection connection = chain.connection();
        Protocol protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
        String requestMessage = request.method() + ' ' + request.url() + ' ' + protocol;
        String requestStartMessage = "--> " + requestMessage;
        if (!logHeaders && hasRequestBody) {
            requestStartMessage += " (" + requestBody.contentLength() + "-byte body)";
        }
        logger.log("start sending Request: " + requestStartMessage, LogUtil.D);


        /*
            请求中,计算请求时间并打印
         */
        long startNs = System.nanoTime();
        Response response;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            logger.log("<-- HTTP FAILED: " + e, LogUtil.D);
            throw e;
        }
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        /*
            请求完后打印RequestBody
         */
        LogUtilHelper.printDivider(LogInterceptor.INTERCEPTOR_TAG_STR);
        logger.log(requestStartMessage, LogUtil.D);

        if (logHeaders) {

            /*打印Content-Type*/
            if (hasRequestBody) {
                String simpleName = requestBody.getClass().getSimpleName();
                if (!TextUtils.isEmpty(simpleName)) {
                    logger.log("Body-Class:" + simpleName, LogUtil.D);
                }

                // Request body headers are only present when installed as a network interceptor. Force
                // them to be included (when available) so there values are known.
                if (requestBody.contentType() != null) {
                    logger.log("Content-Type: " + requestBody.contentType(), LogUtil.D);
                }
                if (requestBody.contentLength() != -1) {
                    logger.log("Content-Length: " + requestBody.contentLength(), LogUtil.D);
                }
            }

            /*打印请求体*/
            Headers headers = request.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                String name = headers.name(i);
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                    logger.log(name + ": " + headers.value(i), LogUtil.D);
                }
            }

            /*请求打印结束*/
            if (!logBody || !hasRequestBody) {
                logger.log("--> END " + request.method(), LogUtil.D);
            } else if (bodyEncoded(request.headers())) {
                logger.log("--> END " + request.method() + " (encoded body omitted)", LogUtil.D);
            } else if (requestBody instanceof MultipartBody) {
                Buffer buffer = new Buffer();
                for (MultipartBody.Part part : ((MultipartBody) requestBody).parts()) {
                    RequestBody body = part.body();
                    Headers partBodyHeaders = part.headers();

                    if (partBodyHeaders != null) {
                        logger.log(partBodyHeaders.toString(), LogUtil.D);
                    }

                    if (body.contentType() == null) {
                        buffer.clear();
                        Charset charset = UTF8;
                        //字符串键值对
                        body.writeTo(buffer);

                        logger.log(buffer.readString(charset), LogUtil.D);
                    } else {
                        try {
                            logger.log(body.contentType().toString() + " "
                                    + ProgressIntercept.formatSize(body.contentLength()), LogUtil.D);

                            Field[] fields = part.body().getClass().getDeclaredFields();
                            for (Field f : fields) {
                                if (f.getName().contains("file")) {
                                    f.setAccessible(true);
                                    File file = (File) f.get(part.body());
                                    logger.log(file.getAbsolutePath(), LogUtil.D);
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
                    logger.log(buffer.readString(charset), LogUtil.D);
                    logger.log("--> END " + request.method() + " (" + requestBody.contentLength() + "-byte body)",
                            LogUtil.D);
                } else {
                    logger.log("--> END "
                            + request.method()
                            + " (binary "
                            + requestBody.contentLength()
                            + "-byte body omitted)", LogUtil.D);
                }
            }
        }

        /* 请求完后打印ResponseBody,  打印返回体 */
        ResponseBody responseBody = response.body();
        long contentLength = responseBody.contentLength();
        String bodySize = contentLength != -1 ? contentLength + "-byte" : "unknown-length";
        logger.log("<-- " + response.code() + ' ' + response.message() + ' ' + response.request()
                .url() + " (" + tookMs + "ms" + (!logHeaders ? ", " + bodySize + " body" : "") + ')', LogUtil.D);

        if (logHeaders) {

            if (logResponse) {
                Headers headers = response.headers();
                for (int i = 0, count = headers.size(); i < count; i++) {
                    logger.log(headers.name(i) + ": " + headers.value(i), LogUtil.D);
                }
            }

            if (!logBody || !HttpHeaders.hasBody(response)) {
                logger.log("<-- END HTTP", LogUtil.D);
            } else if (bodyEncoded(response.headers())) {
                logger.log("<-- END HTTP (encoded body omitted)", LogUtil.D);
            } else {
                BufferedSource source = responseBody.source();
                source.request(Long.MAX_VALUE); // Buffer the entire body.
                Buffer buffer = source.buffer();

                if (logResponse) {
                    Charset charset = UTF8;
                    MediaType contentType = responseBody.contentType();
                    if (contentType != null) {
                        try {
                            charset = contentType.charset(UTF8);
                        } catch (UnsupportedCharsetException e) {

                            logger.log("Couldn't decode the response body; charset is likely malformed.", LogUtil.D);
                            logger.log("<-- END HTTP", LogUtil.D);

                            return response;
                        }
                    }

                    if (!isPlaintext(buffer)) {

                        logger.log("<-- END HTTP (binary " + buffer.size() + "-byte body omitted)", LogUtil.D);
                        return response;
                    }

                    if (contentLength != 0) {

                        logger.log(buffer.clone()
                                .readString(charset), LogUtil.JSON);
                    }
                }
                logger.log("<-- END HTTP: " + requestMessage + " (" + buffer.size() + "-byte body)", LogUtil.D);
            }

            LogUtilHelper.printDivider(LogInterceptor.INTERCEPTOR_TAG_STR);
        }

        return response;
    }

    /**
     * Returns true if the body in question probably contains human readable text. Uses a small sample
     * of code points to detect unicode control characters commonly used in binary file signatures.
     */
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
}