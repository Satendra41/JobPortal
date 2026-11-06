package com.jobportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SessionUserWebConfig implements WebMvcConfigurer {

    private final LoggedInUserRefreshInterceptor loggedInUserRefreshInterceptor;
    private final DynamicHtmlCacheInterceptor dynamicHtmlCacheInterceptor;

    public SessionUserWebConfig(LoggedInUserRefreshInterceptor loggedInUserRefreshInterceptor,
            DynamicHtmlCacheInterceptor dynamicHtmlCacheInterceptor) {
        this.loggedInUserRefreshInterceptor = loggedInUserRefreshInterceptor;
        this.dynamicHtmlCacheInterceptor = dynamicHtmlCacheInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggedInUserRefreshInterceptor).addPathPatterns("/**");
        registry.addInterceptor(dynamicHtmlCacheInterceptor).addPathPatterns("/**");
    }
}
