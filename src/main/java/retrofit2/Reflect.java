package retrofit2;

import android.text.TextUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by angcyo on 2016-11-26.
 */

class Reflect {

    /**
     * 从一个对象中, 获取指定的成员对象
     */
    public static Object getMember(Object target, String member) {
        if (target == null) {
            return null;
        }
        return getMember(target.getClass(), target, member);
    }

    public static Object getMember(Class<?> cls, Object target, String member) {
        Object result = null;
        try {
            Field memberField = cls.getDeclaredField(member);
            memberField.setAccessible(true);
            result = memberField.get(target);
        } catch (Exception e) {
            //L.i("错误:" + cls.getSimpleName() + " ->" + e.getMessage());
        }
        return result;
    }

    /**
     * 只判断String类型的字段, 是否相等
     */
    public static boolean areContentsTheSame(Object target1, Object target2) {
        if (target1 == null || target2 == null) {
            return false;
        }
        if (!target1.getClass().isAssignableFrom(target2.getClass()) ||
                !target2.getClass().isAssignableFrom(target1.getClass())) {
            return false;
        }

        boolean result = true;
        try {
            Field[] declaredFields1 = target1.getClass().getDeclaredFields();
            Field[] declaredFields2 = target2.getClass().getDeclaredFields();
            for (int i = 0; i < declaredFields1.length; i++) {
                declaredFields1[i].setAccessible(true);
                Object v1 = declaredFields1[i].get(target1);

                declaredFields2[i].setAccessible(true);
                Object v2 = declaredFields2[i].get(target2);

                if (v1 instanceof String && v2 instanceof String) {
                    result = TextUtils.equals(((String) v1), (String) v2);
                } else if (v1 instanceof Number && v2 instanceof Number) {
                    result = v1 == v2;
                } else {
                    result = false;
                }

                if (!result) {
                    break;
                }
            }
        } catch (Exception e) {
            //L.i("错误: ->" + e.getMessage());
        }
        return result;
    }

    public static void setMember(Class<?> cls, Object target, String member, Object value) {
        try {
            Field memberField = cls.getDeclaredField(member);
            memberField.setAccessible(true);
            memberField.set(target, value);
        } catch (Exception e) {
            //L.e("错误:" + e.getMessage());
        }
    }

    public static void setMember(Object target, String member, Object value) {
        setMember(target.getClass(), target, member, value);
    }

    /**
     * 获取调用堆栈上一级的方法名称
     */
    public static String getMethodName() {
        final StackTraceElement[] stackTraceElements = new Exception().getStackTrace();
        return stackTraceElements[1].getMethodName();
    }

    /**
     * 通过类对象，运行指定方法
     *
     * @param obj        类对象
     * @param methodName 方法名
     * @param params     参数值
     * @return 失败返回null
     */
    public static Object invokeMethod(Object obj, String methodName, Object... params) {
        if (obj == null || TextUtils.isEmpty(methodName)) {
            return null;
        }

        Class<?> clazz = obj.getClass();
        return invokeMethod(clazz, obj, methodName, params);
    }

    public static Object invokeMethod(Object obj, String methodName, Class<?>[] paramTypes, Object... params) {
        if (obj == null || TextUtils.isEmpty(methodName)) {
            return null;
        }

        Class<?> clazz = obj.getClass();
        return invokeMethod(clazz, obj, methodName, paramTypes, params);
    }

    public static Object invokeMethod(Class<?> cls, Object obj, String methodName, Object... params) {
        if (obj == null || TextUtils.isEmpty(methodName)) {
            return null;
        }

        try {
            Class<?>[] paramTypes = null;
            if (params != null && params.length > 0) {
                paramTypes = new Class[params.length];
                for (int i = 0; i < params.length; ++i) {
                    Class<?> pClass = params[i].getClass();

                    if (pClass.getName().contains("Integer")) {
                        paramTypes[i] = int.class;
                    } else if (pClass.getName().contains("Long")) {
                        paramTypes[i] = long.class;
                    } else if (pClass.getName().contains("Float")) {
                        paramTypes[i] = float.class;
                    } else if (pClass.getName().contains("Double")) {
                        paramTypes[i] = double.class;
                    } else {
                        paramTypes[i] = pClass;
                    }
                }
            }
            Method method = cls.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            Object invoke = method.invoke(obj, params);
            return invoke;
        } catch (Exception e) {
            //L.e("错误:" + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static Object invokeMethod(Class<?> cls, Object obj, String methodName, Class<?>[] paramTypes, Object... params) {
        if (obj == null || TextUtils.isEmpty(methodName)) {
            return null;
        }

        try {
            Method method = cls.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            Object invoke = method.invoke(obj, params);
            return invoke;
        } catch (Exception e) {
            //L.e("错误:" + e.getMessage());
        }
        return null;
    }

    /**
     * 通过反射, 获取obj对象的 指定成员变量的值
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || TextUtils.isEmpty(fieldName)) {
            return null;
        }

        Class<?> clazz = obj.getClass();
        while (clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception e) {
                //L.e("错误:" + e.getMessage());
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * 设置字段的值
     */
    public static void setFieldValue(Object obj, String fieldName, Object value) {
        if (obj == null || TextUtils.isEmpty(fieldName)) {
            return;
        }

        Class<?> clazz = obj.getClass();
        while (clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (Exception e) {
                //L.e("错误:" + e.getMessage());
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 通过类型, 创建实例
     */
    public static <T> T newObject(Class cls) {
        T obj = null;
        try {
            Constructor constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            obj = (T) constructor.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj;
    }


    public static String logException(Exception e) {
        String message = e.getMessage();
        if (e instanceof InvocationTargetException) {
            message = ((InvocationTargetException) e).getTargetException().getMessage();
        }
        if (TextUtils.isEmpty(message)) {
            message = e.toString();
        }
        return message;
    }
}
