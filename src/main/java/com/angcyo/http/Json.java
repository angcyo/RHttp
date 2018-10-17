package com.angcyo.http;

import android.util.JsonReader;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016,深圳市红鸟网络科技股份有限公司 All rights reserved.
 * 项目名称：
 * 类的描述：
 * 创建人员：Robi
 * 创建时间：2016/12/01 14:42
 * 修改人员：Robi
 * 修改时间：2016/12/01 14:42
 * 修改备注：
 * Version: 1.0.0
 */
public class Json {
    public static <T> T from(String json, Class<T> type) {
        Gson gson = new Gson();
        return gson.fromJson(json, type);
    }

    public static <T> T from(String json, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(json, type);
    }

    public static String to(Object obj) {
        Gson gson = new Gson();
        return gson.toJson(obj);
    }

    public static <T> T from2(String json, Class<T> type) {
        T result = null;
        JsonReader jsonReader = new JsonReader(new StringReader(json));
        try {
            result = readerObject(jsonReader, type);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                jsonReader.close();
            } catch (IOException e) {

            }
        }
        return result;
    }

    private static <T> T readerObject(JsonReader jsonReader, Class<T> type) {
        T result = null;
        try {
            result = (T) Class.forName(type.getName()).newInstance();
            Field[] fields = result.getClass().getDeclaredFields();
            Map<String, Field> fieldsMap = new HashMap<>();
            for (Field field : fields) {
                fieldsMap.put(field.getName(), field);
            }

            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                try {
                    String nextName = jsonReader.nextName();
                    Field field = fieldsMap.get(nextName);
                    field.setAccessible(true);

                    Class<?> fieldType = field.getType();
                    Type genericType = field.getGenericType();
                    if (genericType instanceof ParameterizedType) {
                        //具有泛型的type, 比如List<String>
                        Type subType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                        Class<?> subClass = Class.forName(((Class) subType).getName());
                        if (fieldType.isAssignableFrom(List.class)) {
                            //field.set(result, );
                            List list = new ArrayList<>();
                            field.set(result, list);

                            readerList(jsonReader, list, subClass);
                        } else {
                            //不支持的类型, 跳过
                            jsonReader.skipValue();
                        }
                    } else {
                        if (fieldType.isAssignableFrom(int.class)) {
                            field.set(result, jsonReader.nextInt());
                        } else if (fieldType.isAssignableFrom(long.class)) {
                            field.set(result, jsonReader.nextLong());
                        } else if (fieldType.isAssignableFrom(float.class) ||
                                fieldType.isAssignableFrom(double.class)) {
                            field.set(result, jsonReader.nextDouble());
                        } else if (fieldType.isAssignableFrom(boolean.class)) {
                            field.set(result, jsonReader.nextBoolean());
                        } else if (fieldType.isAssignableFrom(String.class)) {
                            field.set(result, jsonReader.nextString());
                        } else {
                            field.set(result, readerObject(jsonReader, fieldType));
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                    jsonReader.skipValue();
                }
            }
            jsonReader.endObject();

        } catch (Exception e) {
            //e.printStackTrace();
            try {
                jsonReader.skipValue();
            } catch (IOException e1) {
                //e1.printStackTrace();
            }
        }
        return result;
    }

    private static void readerList(JsonReader jsonReader, List list, Class<?> fieldType) {
        try {
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                try {
                    if (fieldType.isAssignableFrom(int.class)) {
                        list.add(jsonReader.nextInt());
                    } else if (fieldType.isAssignableFrom(long.class)) {
                        list.add(jsonReader.nextLong());
                    } else if (fieldType.isAssignableFrom(float.class) ||
                            fieldType.isAssignableFrom(double.class)) {
                        list.add(jsonReader.nextDouble());
                    } else if (fieldType.isAssignableFrom(boolean.class)) {
                        list.add(jsonReader.nextBoolean());
                    } else if (fieldType.isAssignableFrom(String.class)) {
                        list.add(jsonReader.nextString());
                    } else {
                        list.add(readerObject(jsonReader, fieldType));
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                    jsonReader.skipValue();
                }
            }
            jsonReader.endArray();
        } catch (IOException e) {
            //e.printStackTrace();
            try {
                jsonReader.skipValue();
            } catch (IOException e1) {
                //e1.printStackTrace();
            }
        }
    }
}
