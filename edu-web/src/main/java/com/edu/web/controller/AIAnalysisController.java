package com.edu.web.controller;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.service.DeepSeekService;
import lombok.RequiredArgsConstructor;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edu/ai")
@RequiredArgsConstructor
public class AIAnalysisController {
   private final DeepSeekService deepSeekService;
    
    
    @PostMapping("/analyze")
    public ResponseEntity<AiSuggestionDTO> analyzeData(@RequestBody Map<String, String> request) {
        String dataType = request.get("dataType");
        String dataJson = request.get("dataJson");
        
        AiSuggestionDTO result = deepSeekService.analyzeData(dataJson, dataType);
        return ResponseEntity.ok(result);
    }
}
