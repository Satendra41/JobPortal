package com.jobportal.config;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Prevents cached server-rendered pages from hiding newer database state. */
@Component
public class DynamicHtmlCacheInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)
                || !("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod()))) {
            return true;
        }
        Class<?> controller = method.getBeanType();
        if (AnnotatedElementUtils.hasAnnotation(controller, Controller.class)
                && !AnnotatedElementUtils.hasAnnotation(controller, RestController.class)) {
            response.setHeader("Cache-Control", "private, no-store, no-cache, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
        return true;
    }
}
