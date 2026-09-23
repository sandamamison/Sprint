package util;

import java.util.Objects;

import annotation.GetMapping;
import annotation.PostMapping;
import annotation.UrlMapping;

public class UrlMethod {

    private final String url;
    private final String method;

    public UrlMethod(String url, String method) {
        this.url = url;
        this.method = (method == null || method.isBlank()) ? "GET" : method.trim().toUpperCase();
    }

    public UrlMethod(UrlMapping annotation) {
        this(annotation.url(), annotation.method());
    }

    public UrlMethod(GetMapping annotation) {
        this(annotation.url(), "GET");
    }

    public UrlMethod(PostMapping annotation) {
        this(annotation.url(), "POST");
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlMethod that = (UrlMethod) o;
        return Objects.equals(url, that.url) && Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method);
    }

    @Override
    public String toString() {
        return method + " " + url;
    }
}
