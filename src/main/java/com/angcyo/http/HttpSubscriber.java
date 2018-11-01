package com.angcyo.http;

import android.support.annotation.Nullable;
import com.angcyo.http.log.LogUtil;
import rx.Subscriber;

/**
 * Email:angcyo@126.com
 *
 * @author angcyo
 * @date 2018/10/15
 */
public class HttpSubscriber<T> extends Subscriber<T> {
    T data = null;

    @Override
    public void onStart() {
        super.onStart();
        LogUtil.d();
    }

    @Override
    public void onCompleted() {
        //LogUtil.d();
        onEnd(data, null);
    }

    @Override
    public void onError(Throwable e) {
        //LogUtil.e(e);
        onEnd(null, e);
    }

    @Override
    public void onNext(T t) {
        //LogUtil.w(t);
        data = t;
        onSucceed(t);
    }

    public void onSucceed(T data) {
        //LogUtil.i(data);
    }

    public void onEnd(@Nullable T data /*如果成功, 才有值*/, @Nullable Throwable error /*如果失败, 才有值*/) {
        if (error == null) {
            LogUtil.i(LogUtil.mGlobalTag, data, error);
        } else {
            LogUtil.e(LogUtil.mGlobalTag, data, error);
        }
    }

}
