package com.angcyo.http;

import android.support.annotation.Nullable;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.observables.SyncOnSubscribe;
import rx.schedulers.Schedulers;

/**
 * Created by robi on 2016-04-21 15:41.
 */
public class Rx<Rx> extends Observable<Rx> {

    /**
     * Creates an Observable with a Function to execute when it is subscribed to.
     * <p>
     * <em>Note:</em> Use {@link #unsafeCreate(OnSubscribe)} to create an Observable, instead of this constructor,
     * unless you specifically have a need for inheritance.
     */
    protected Rx(OnSubscribe<Rx> f) {
        super(f);
    }

    public static <T> Observable.Transformer<T, T> normalSchedulers() {
        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> source) {
                return source.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread());
            }
        };
    }

    /**
     * <pre>
     *
     * Rx.base(object : RFunc<HomeBean>() {
     *      override fun call(t: String?): HomeBean? {
     *      return null
     *  }
     * }, object : HttpSubscriber<HomeBean>() {
     *
     * })
     *
     * </pre>
     * 简单的子线程,转主线程调用
     */
    public static <R> Subscription base(RFunc<? extends R> onBack, HttpSubscriber<R> onMain) {
        return Observable
                .just("base")
                .map(onBack)
                .compose(new Transformer<R, R>() {
                    @Override
                    public Observable<R> call(Observable<R> rObservable) {
                        return rObservable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
                    }
                })
                .subscribe(onMain);
    }


    /**
     * 切换执行
     */
    public static Subscription base(final Runnable onBack, final Runnable onMain) {
        return base(
                new RFunc<String>() {
                    @Override
                    public String onFuncCall() {
                        onBack.run();
                        return "";
                    }
                },
                new HttpSubscriber<String>() {
                    @Override
                    public void onSucceed(String bean) {
                        super.onSucceed(bean);
                        onMain.run();
                    }
                });
    }

    /**
     * 主线程执行
     */
    public static Subscription main(final Runnable onMain) {
        return base(
                new RFunc<String>() {
                    @Override
                    public String onFuncCall() {
                        return "";
                    }
                },
                new HttpSubscriber<String>() {
                    @Override
                    public void onSucceed(String bean) {
                        super.onSucceed(bean);
                        onMain.run();
                    }
                });
    }

    /**
     * 后台执行
     */
    public static Subscription back(final Runnable onBack) {
        return base(
                new RFunc<String>() {
                    @Override
                    public String onFuncCall() {
                        onBack.run();
                        return "";
                    }
                },
                new HttpSubscriber<String>() {

                    @Override
                    public void onStart() {
                        //super.onStart();
                    }

                    @Override
                    public void onSucceed(String bean) {
                        super.onSucceed(bean);
                    }

                    @Override
                    public void onEnd(@Nullable String data, @Nullable Throwable error) {
                        //super.onEnd(data, error);
                    }
                });
    }


    public static <T> Observable.Transformer<T, T> transformer() {
        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> tObservable) {
                return tObservable.unsubscribeOn(Schedulers.io())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread());
            }
        };
    }

    public static <T> Observable<T> create(final Func<T> doFunc) {
        return create(new SyncOnSubscribe<Integer, T>() {
            @Override
            protected Integer generateState() {
                return 1;
            }

            @Override
            protected Integer next(Integer state, Observer<? super T> observer) {
                //L.e("next-----() -> " + state);
                if (state <= 0) {
                    observer.onCompleted();
                } else {
                    observer.onNext(doFunc.call(observer));
                }
                return 0;
            }
        }).compose(Http.<T>defaultTransformer());
    }

    /**
     * 基础用法
     */
    public static <T> Observable<T> get(final Func<T> doFunc) {
        return create(doFunc);
    }
}
