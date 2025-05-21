package com.example.demo.Controller;

import com.example.demo.entity.entityInterface.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class TestController {
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal AppUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "role", user.getRole(),
                "id", user.getId()
        ));
    }

}
