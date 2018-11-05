package com.angcyo.http;

/**
 * Email:angcyo@126.com
 *
 * @author angcyo
 * @date 2018/11/02
 */
public class HttpException extends RuntimeException {

    String body;

    public HttpException(Throwable cause, String body) {
        super(cause);
        this.body = body;
    }

    @Override
    public String toString() {
        return (body == null) ? super.toString() : "body:" + body + " " + super.toString();
    }
}
