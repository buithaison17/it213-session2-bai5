package com.example.bai5;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackAnalysisService {
    private final ChatModel chatModel;

    public String analyzeFeedback(String feedbackText) {
        Prompt prompt = new Prompt(feedbackText);
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }
}
