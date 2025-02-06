package com.webchat.wchat.service;

import com.webchat.wchat.model.ChatMessage;
import com.webchat.wchat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final ChatMessageRepository chatMessageRepository;

    public void saveFile(ChatMessage message, MultipartFile file) throws IOException {
        message.setFileName(file.getOriginalFilename());
        message.setFileType(file.getContentType());
        message.setFileData(file.getBytes());
    }

    public ChatMessage getFile(Long messageId) {
        return chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
    }
}

