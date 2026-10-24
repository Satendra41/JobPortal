package com.jobportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SessionUserWebConfig implements WebMvcConfigurer {

    private final LoggedInUserRefreshInterceptor loggedInUserRefreshInterceptor;

    public SessionUserWebConfig(LoggedInUserRefreshInterceptor loggedInUserRefreshInterceptor) {
        this.loggedInUserRefreshInterceptor = loggedInUserRefreshInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggedInUserRefreshInterceptor).addPathPatterns("/**");
    }
}
