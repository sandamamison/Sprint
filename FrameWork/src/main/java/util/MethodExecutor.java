package util;

import annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.FileUpload;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import model.FileUpload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MethodExecutor {
    public static Object execute(Method method) throws Exception {
        return execute(method, null, null);
    }

    public static Object execute(Method method, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (method == null) {
            throw new IllegalArgumentException("La methode ne peut pas etre null");
        }

        Object controller = method.getDeclaringClass().getDeclaredConstructor().newInstance();
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];

        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            Class<?> parameterType = parameter.getType();
            String parameterName = getParameterName(parameter);

            if (parameterType.equals(HttpServletRequest.class)) {
                arguments[index] = request;
            } else if (parameterType.equals(HttpServletResponse.class)) {
                arguments[index] = response;
            } else if (parameterType.equals(FileUpload.class)) {
                arguments[index] = extractFileUpload(parameterName, request);
            } else if (isSimpleType(parameterType)) {
                arguments[index] = extractSimpleValue(parameterName, parameterType, request);
            } else {
                arguments[index] = bindObject(parameterType, request);
            }
        }

        return method.invoke(controller, arguments);
    }

    private static String getParameterName(Parameter parameter) {
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        return requestParam == null ? parameter.getName() : requestParam.value();
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.equals(String.class)
                || type.equals(Integer.class) || type.equals(int.class)
                || type.equals(Long.class) || type.equals(long.class)
                || type.equals(Double.class) || type.equals(double.class)
                || type.equals(Float.class) || type.equals(float.class)
                || type.equals(Boolean.class) || type.equals(boolean.class)
                || type.equals(Short.class) || type.equals(short.class)
                || type.equals(Byte.class) || type.equals(byte.class)
                || type.equals(Character.class) || type.equals(char.class)
                || type.isEnum();
    }

    private static Object extractSimpleValue(String name, Class<?> type, HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Une requete est necessaire pour le parametre: " + name);
        }
        String value = request.getParameter(name);
        if (value == null) {
            if (type.isPrimitive()) {
                throw new IllegalArgumentException("Missing parameter: " + name);
            }
            return null;
        }
        try {
            if (type.equals(String.class)) return value;
            if (type.equals(Integer.class) || type.equals(int.class)) return Integer.valueOf(value);
            if (type.equals(Long.class) || type.equals(long.class)) return Long.valueOf(value);
            if (type.equals(Double.class) || type.equals(double.class)) return Double.valueOf(value);
            if (type.equals(Float.class) || type.equals(float.class)) return Float.valueOf(value);
            if (type.equals(Boolean.class) || type.equals(boolean.class)) return Boolean.valueOf(value);
            if (type.equals(Short.class) || type.equals(short.class)) return Short.valueOf(value);
            if (type.equals(Byte.class) || type.equals(byte.class)) return Byte.valueOf(value);
            if (type.equals(Character.class) || type.equals(char.class)) {
                if (value.length() != 1) {
                    throw new IllegalArgumentException();
                }
                return value.charAt(0);
            }
            return Enum.valueOf(type.asSubclass(Enum.class), value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid parameter '" + name + "' for type " + type.getSimpleName(), exception);
        }
    }

    private static FileUpload extractFileUpload(String name, HttpServletRequest request) throws Exception {
        if (request == null || request.getContentType() == null
                || !request.getContentType().toLowerCase().startsWith("multipart/form-data")) {
            return null;
        }
        jakarta.servlet.http.Part part = request.getPart(name);
        if (part == null || part.getSubmittedFileName() == null || part.getSubmittedFileName().isEmpty()) {
            return null;
        }

        try (InputStream input = part.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new FileUpload(part.getSubmittedFileName(), output.toByteArray());
        }
    }

    private static Object bindObject(Class<?> type, HttpServletRequest request) throws Exception {
        Object result = type.getDeclaredConstructor().newInstance();
        for (Class<?> currentType = type; currentType != null; currentType = currentType.getSuperclass()) {
            for (Field field : currentType.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                if (field.getType().equals(FileUpload.class)) {
                    FileUpload file = extractFileUpload(field.getName(), request);
                    if (file != null) {
                        field.set(result, file);
                    }
                } else if (isSimpleType(field.getType())) {
                    Object value = extractSimpleValue(field.getName(), field.getType(), request);
                    if (value != null) {
                        field.set(result, value);
                    }
                }
            }
        }
        return result;
    }
}
