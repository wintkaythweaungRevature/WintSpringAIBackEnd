package com.example;

import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
// ✅ Corrected import paths for Spring AI 1.0.0-M1
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = "*") 
public class AudioController {

    private final OpenAiAudioTranscriptionModel transcriptionModel;

    public AudioController(OpenAiAudioTranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @PostMapping("/transcribe")
    public ResponseEntity<String> transcribe(@RequestParam("file") MultipartFile file) {
        try {
            // Options setup
            var transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .withResponseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .withLanguage("en")
                .withTemperature(0f)
                .build();

            // ✅ This now matches the corrected imports above
            AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(file.getResource(), transcriptionOptions);
            
            AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);

            return new ResponseEntity<>(response.getResult().getOutput(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}