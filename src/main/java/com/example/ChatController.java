package com.example;


import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "https://wintkaythweaung.com")

@RequestMapping("/api/ai")
@RestController
public class ChatController {
    
    @GetMapping("/ask-ai")
    public String askAi(@RequestParam(value = "prompt") String prompt) {
        return chatModel.call(prompt);
    }
}