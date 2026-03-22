package com.picmeup.common.config;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class SpaForwardController implements ErrorController {

    @GetMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status != null && Integer.parseInt(status.toString()) == HttpStatus.NOT_FOUND.value()) {
            String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            if (path != null && !path.startsWith("/api/") && !path.startsWith("/actuator/")) {
                return "forward:/index.html";
            }
        }
        return "forward:/error-page";
    }
}
