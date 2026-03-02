package com.example;

import lombok.Data;

@Data    
public class EmailRequest {
    private String emailContent;
    private String tone; 
    // No manual getters needed here! @Data handles it.
}
