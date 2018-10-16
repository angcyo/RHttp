package com.angcyo.http;

import android.text.TextUtils;
import okhttp3.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static android.os.Build.UNKNOWN;

/**
 * Copyright (C) 2016,深圳市红鸟网络科技股份有限公司 All rights reserved.
 * 项目名称：
 * 类的描述：
 * 创建人员：Robi
 * 创建时间：2017/05/10 14:00
 * 修改人员：Robi
 * 修改时间：2017/05/10 14:00
 * 修改备注：
 * Version: 1.0.0
 */
public class Ok {
    private final Map<String, ImageType> imageTypeCache = new ConcurrentHashMap<>();
    private OkHttpClient sOkHttpClient;

    private Ok() {
    }

    public static Ok instance() {
        return Holder.instance;
    }

    private void ensureClient() {
        if (sOkHttpClient == null) {
            sOkHttpClient = new OkHttpClient.Builder()
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
    }

    public void load(String url) {
        try {
            Call call = getCall(url, null);
            Response response = call.execute();
            response.body().byteStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Call getCall(String url, OnImageTypeListener listener) {
        ensureClient();
        Call call = null;
        try {
            Request mRequest = new Request.Builder().url(url).build();//如果url不是 网址, 会报错
            call = sOkHttpClient.newCall(mRequest);
        } catch (Exception e) {
            e.printStackTrace();
            if (listener != null) {
                typeCheckEnd(url, "UNKNOWN", listener);
            }
        }
        return call;
    }

    /**
     * 根据url, 回调对应图片的类型
     */
    public void type(final String url, final OnImageTypeListener listener) {
        if (TextUtils.isEmpty(url)) {
            if (listener != null) {
                listener.onImageType(url, ImageType.UNKNOWN);
            }
            return;
        }

        ImageType type = imageTypeCache.get(url);

        if (listener != null) {
            listener.onLoadStart();
        }

        if (type == null || type == ImageType.UNKNOWN) {
            File file = new File(url);
            if (file.exists()) {
                String imageType = ImageTypeUtil.getImageType(file);
                typeCheckEnd(url, imageType, listener);
            } else {
                Call call = getCall(url, listener);
                if (call == null) {
                    typeCheckEnd(url, "UNKNOWN", listener);
                } else {
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            typeCheckEnd(url, UNKNOWN, listener);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            final String imageType = ImageTypeUtil.getImageType(response.body().byteStream());
                            typeCheckEnd(url, imageType, listener);
                        }
                    });
                }
            }
        } else {
            if (listener != null) {
                listener.onImageType(url, type);
            }
        }
    }

    private void typeCheckEnd(final String url, String imageType, final OnImageTypeListener listener) {
        final ImageType imageType1 = ImageType.of(imageType);
        imageTypeCache.put(url, imageType1);
        if (listener != null) {
            ThreadExecutor.instance().onMain(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onImageType(url, imageType1);
                    }
                }
            });
        }
    }

    public enum ImageType {
        JPEG, GIF, PNG, BMP, WEBP, UNKNOWN;

        public static ImageType of(String type) {
            if (TextUtils.isEmpty(type)) {
                return UNKNOWN;
            }
            if ("JPEG".equalsIgnoreCase(type)) {
                return JPEG;
            }
            if ("GIF".equalsIgnoreCase(type)) {
                return GIF;
            }
            if ("PNG".equalsIgnoreCase(type)) {
                return PNG;
            }
            if ("BMP".equalsIgnoreCase(type)) {
                return BMP;
            }
            if ("WEBP".equalsIgnoreCase(type)) {
                return WEBP;
            }
            return UNKNOWN;
        }
    }

    public interface OnImageTypeListener {
        void onImageType(String imageUrl, ImageType imageType);

        void onLoadStart();
    }

    private static class Holder {
        static Ok instance = new Ok();
    }

    public static class ImageTypeUtil {

        public static String getImageType(File file) {
            if (file == null || !file.isFile() || !file.canRead()) return null;
            InputStream is = null;
            try {
                is = new FileInputStream(file);
                return getImageType(is);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public static String getImageType(InputStream is) {
            if (is == null) return null;
            try {
                byte[] bytes = new byte[8];
                return is.read(bytes, 0, 8) != -1 ? getImageType(bytes) : null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        private static String getImageType(byte[] bytes) {
            if (isJPEG(bytes)) return "JPEG";
            if (isGIF(bytes)) return "GIF";
            if (isPNG(bytes)) return "PNG";
            if (isBMP(bytes)) return "BMP";
            if (isWebP(bytes)) return "WEBP";
            return null;
        }

        private static boolean isJPEG(byte[] b) {
            return b.length >= 2
                    && (b[0] == (byte) 0xFF) && (b[1] == (byte) 0xD8);
        }

        private static boolean isGIF(byte[] b) {
            return b.length >= 6
                    && b[0] == 'G' && b[1] == 'I'
                    && b[2] == 'F' && b[3] == '8'
                    && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
        }

        private static boolean isPNG(byte[] b) {
            return b.length >= 8
                    && (b[0] == (byte) 137 && b[1] == (byte) 80
                    && b[2] == (byte) 78 && b[3] == (byte) 71
                    && b[4] == (byte) 13 && b[5] == (byte) 10
                    && b[6] == (byte) 26 && b[7] == (byte) 10);
        }

        private static boolean isBMP(byte[] b) {
            return b.length >= 2
                    && (b[0] == 0x42) && (b[1] == 0x4d);
        }

        private static boolean isWebP(byte[] b) {
            return b.length >= 4
                    && b[0] == (byte) 82 && b[1] == (byte) 73
                    && b[2] == (byte) 70 && b[3] == (byte) 70;
        }
    }

}
