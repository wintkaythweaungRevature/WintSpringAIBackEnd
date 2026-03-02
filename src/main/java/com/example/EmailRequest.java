package com.example;
import lombok.Data; 

@Data           
public class EmailRequest {
    private String emailContent;
    private String tone; // e.g., "formal", "casual", "friendly"
    
}
