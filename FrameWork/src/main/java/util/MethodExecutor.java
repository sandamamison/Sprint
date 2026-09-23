package util;

import java.lang.reflect.Method;

public class MethodExecutor {
    public static Object execute (Method method) throws Exception {
        if (method == null) {
            System.err.println("Methode ne peut pas etre null");
        }
        Class<?> classController = method.getDeclaringClass();
        Object constructor = classController.getDeclaredConstructor().newInstance();

        return method.invoke(constructor);
    }
}