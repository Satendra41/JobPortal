package com.jobportal.config;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.jobportal.model.User;
import com.jobportal.services.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class LoggedInUserRefreshInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public LoggedInUserRefreshInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }
        Object sessionUser = session.getAttribute("loggedInUser");
        if (!(sessionUser instanceof User cachedUser)) {
            return true;
        }

        User currentUser = cachedUser.getId() == null ? null : userService.getEnabledUserForSession(cachedUser.getId());
        if (currentUser == null || cachedUser.getCredentialVersion() != currentUser.getCredentialVersion()) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        session.setAttribute("loggedInUser", currentUser);
        return true;
    }
}
