package com.example;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Data;

@Service
public class EmailGeneratorService {
    @Value("${spring.ai.openai.api-url}")

    private String OpenaiApiUrl ;
     @Value("${spring.ai.openai.api-key}")
    private String OpenaiApiKey;

    public String generateEmail(EmailRequest emailRequest) {
      //build ppromt , craft request , do resquest and get response and return response
      String prompt = buildPrompt(emailRequest);
      Map<String, Object> requestBody  = Map.of
      ("contents",new Object[] 
      {Map.of("parts",new Object[] {
        Map.of("text",prompt)})});
        //do request 

        //return response 
            return prompt;


        }
        private String buildPrompt(EmailRequest emailRequest) {
            StringBuilder  prompt = new StringBuilder();
            prompt.append("Generate a professional email reply forthe following email content.Please don't generate a subject line ");
             if(emailRequest.getTone() !=null && !emailRequest.getTone().isEmpty()) {
                 prompt.append("Use a ").append(emailRequest.getTone()).append(" tone. ");
            
                
    }
    prompt.append("\nOriginal email: \n").append(emailRequest.getEmailContent());
    return prompt.toString();
        }
}