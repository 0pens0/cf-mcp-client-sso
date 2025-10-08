package org.tanzu.mcpclient.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/motivation")
public class MotivationController {

    @Autowired
    private ChatClient chatClient;

    private final List<String> predefinedMessages = List.of(
        "You've got this! 💪",
        "Keep pushing forward! 🚀", 
        "Every step counts! 👣",
        "Believe in yourself! ✨",
        "You're doing amazing! 🌟"
    );

    private final Random random = new Random();

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateMotivationMessage() {
        try {
            // Use ChatClient to generate motivational message
            String aiMessage = chatClient.prompt()
                .user("Generate a short, uplifting motivational message (max 50 characters) to inspire someone. Make it positive, encouraging, and include an emoji. Examples: \"You've got this! 💪\", \"Keep going! 🚀\", \"Believe in yourself! ✨\"")
                .call()
                .content();
            
            // Clean up the message and ensure it's not too long
            String cleanMessage = aiMessage.trim();
            if (cleanMessage.length() > 50) {
                cleanMessage = cleanMessage.substring(0, 47) + "...";
            }
            
            return ResponseEntity.ok(Map.of("message", cleanMessage));
            
        } catch (Exception e) {
            // Fallback to predefined message if AI fails
            String fallbackMessage = predefinedMessages.get(random.nextInt(predefinedMessages.size()));
            return ResponseEntity.ok(Map.of("message", fallbackMessage));
        }
    }

    @GetMapping("/predefined")
    public ResponseEntity<List<String>> getPredefinedMessages() {
        return ResponseEntity.ok(predefinedMessages);
    }
}
