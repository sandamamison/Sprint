package util;

import annotation.Auth;
import annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import model.FileUpload;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public class MethodExecutor {
    public static Object execute(Method method) throws Exception {
        return execute(method, null, null);
    }

    public static Object execute(Method method, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (method == null) {
            throw new IllegalArgumentException("La methode ne peut pas etre null");
        }

        checkAuthorization(method, request);

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
            } else if (parameterType.equals(HttpSession.class)) {
                if (request == null) {
                    throw new IllegalArgumentException("Une requete est necessaire pour injecter HttpSession");
                }
                arguments[index] = request.getSession();
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

    private static void checkAuthorization(Method method, HttpServletRequest request) {
        Auth auth = method.getAnnotation(Auth.class);
        if (auth == null) {
            auth = method.getDeclaringClass().getAnnotation(Auth.class);
        }
        if (auth == null) {
            return;
        }

        if (request == null) {
            throw new SecurityException("Acces refuse : requete manquante pour verifier l'authentification");
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new SecurityException("Acces refuse : vous devez etre connecte pour acceder a cette ressource");
        }

        String sessionUserKey = null;
        String sessionRoleKey = null;
        try {
            if (request.getServletContext() != null) {
                sessionUserKey = request.getServletContext().getInitParameter("auth-session-key");
                sessionRoleKey = request.getServletContext().getInitParameter("auth-role-key");
            }
        } catch (Throwable ignored) {
        }

        Object user = getSessionUser(session, sessionUserKey);
        if (user == null) {
            throw new SecurityException("Acces refuse : vous devez etre connecte pour acceder a cette ressource");
        }

        List<String> requiredRoles = getRequiredRoles(auth);
        if (!requiredRoles.isEmpty()) {
            String role = getSessionRole(session, user, sessionRoleKey);
            if (role == null || !hasRole(role, requiredRoles)) {
                throw new SecurityException("Acces refuse : permissions insuffisantes (role requis : " + requiredRoles + ")");
            }
        }
    }

    private static Object getSessionUser(HttpSession session, String customKey) {
        if (customKey != null && !customKey.trim().isEmpty()) {
            Object val = session.getAttribute(customKey.trim());
            if (isValidUser(val)) {
                return val;
            }
        }
        String[] defaultKeys = {"user", "username", "auth", "profile", "connectedUser", "login"};
        for (String key : defaultKeys) {
            Object val = session.getAttribute(key);
            if (isValidUser(val)) {
                return val;
            }
        }
        Object isConnected = session.getAttribute("isConnected");
        if (Boolean.TRUE.equals(isConnected)) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static boolean isValidUser(Object val) {
        if (val == null) {
            return false;
        }
        if (val instanceof String) {
            return !((String) val).trim().isEmpty();
        }
        return true;
    }

    private static List<String> getRequiredRoles(Auth auth) {
        List<String> roles = new ArrayList<>();
        if (auth.value() != null) {
            for (String r : auth.value()) {
                if (r != null && !r.trim().isEmpty()) {
                    roles.add(r.trim());
                }
            }
        }
        if (auth.role() != null) {
            for (String r : auth.role()) {
                if (r != null && !r.trim().isEmpty()) {
                    roles.add(r.trim());
                }
            }
        }
        return roles;
    }

    private static String getSessionRole(HttpSession session, Object user, String customRoleKey) {
        if (customRoleKey != null && !customRoleKey.trim().isEmpty()) {
            Object val = session.getAttribute(customRoleKey.trim());
            if (val != null) {
                return String.valueOf(val).trim();
            }
        }
        String[] defaultRoleKeys = {"role", "profile", "userRole"};
        for (String key : defaultRoleKeys) {
            Object val = session.getAttribute(key);
            if (val != null) {
                return String.valueOf(val).trim();
            }
        }
        if (user != null && !(user instanceof String) && !(user instanceof Boolean)) {
            try {
                Method getRoleMethod = user.getClass().getMethod("getRole");
                Object r = getRoleMethod.invoke(user);
                if (r != null) {
                    return String.valueOf(r).trim();
                }
            } catch (Exception ignored) {
            }
            try {
                Method getProfileMethod = user.getClass().getMethod("getProfile");
                Object r = getProfileMethod.invoke(user);
                if (r != null) {
                    return String.valueOf(r).trim();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean hasRole(String userRole, List<String> requiredRoles) {
        for (String required : requiredRoles) {
            if (userRole.equalsIgnoreCase(required)) {
                return true;
            }
        }
        return false;
    }
}
