package com.angcyo.http;

import rx.Observer;
import rx.functions.Func1;

/**
 * Created by angcyo on 2018-03-04.
 */

public interface Func<T> extends Func1<Observer, T> {
}
