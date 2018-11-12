package com.angcyo.http;

import rx.functions.Func1;

/**
 * Copyright (C) 2016,深圳市红鸟网络科技股份有限公司 All rights reserved.
 * 项目名称：
 * 类的描述：
 * 创建人员：Robi
 * 创建时间：2017/06/16 15:57
 * 修改人员：Robi
 * 修改时间：2017/06/16 15:57
 * 修改备注：
 * Version: 1.0.0
 */
public abstract class RFunc<R> implements Func1<String, R> {
    @Override
    public R call(String s) {
        return onFuncCall();
    }

    public abstract R onFuncCall();
}
