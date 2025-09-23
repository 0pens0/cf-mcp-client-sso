package org.tanzu.mcpclient.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/welcome";
        }
        return "redirect:/login.html";
    }

    @GetMapping("/app")
    public String app() {
        // Serve the Angular index from the built frontend
        return "forward:/index.html";
    }

}
