package org.tanzu.mcpclient.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WelcomeController {

    @GetMapping("/welcome")
    public String welcome(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal != null) {
            // Try to get user name from different possible attributes
            String userName = principal.getAttribute("name");
            if (userName == null) {
                userName = principal.getAttribute("preferred_username");
            }
            if (userName == null) {
                userName = principal.getAttribute("sub");
            }
            if (userName == null) {
                userName = principal.getName();
            }
            
            model.addAttribute("userName", userName != null ? userName : "User");
            model.addAttribute("userAttributes", principal.getAttributes());
        }
        return "welcome";
    }
}




