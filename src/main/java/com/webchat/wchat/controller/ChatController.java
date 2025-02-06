package com.webchat.wchat.controller;

import com.webchat.wchat.chat.MessageType;
import com.webchat.wchat.model.ChatMessage;
import com.webchat.wchat.model.ChatRoom;
import com.webchat.wchat.model.User;
import com.webchat.wchat.repository.ChatRoomRepository;
import com.webchat.wchat.service.ChatService;
import com.webchat.wchat.service.FileStorageService;
import com.webchat.wchat.service.UserService;
import com.webchat.wchat.repository.ChatMessageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileStorageService fileStorageService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private static final int MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/chat")
    public String getChatPage(Principal principal, Model model) {
        if (principal != null) {
            String username = principal.getName();
            User user = userService.findByUsername(username);
            ChatRoom publicRoom = chatService.getOrCreatePublicRoom();

            // Ensure user is in public room
            userService.addUserToPublicRoom(user);

            List<ChatRoom> rooms = chatService.getUserRooms(username);
            if (!rooms.contains(publicRoom)) {
                rooms.add(0, publicRoom);
            }

            model.addAttribute("username", username);
            model.addAttribute("rooms", rooms);
            model.addAttribute("defaultRoomId", publicRoom.getId());
        }
        return "chat";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/chat/room")
    @ResponseBody
    public ResponseEntity<ChatRoom> createRoom(@RequestBody Map<String, Object> payload, Principal principal) {
        try {
            String name = (String) payload.get("name");
            List<String> participants = (List<String>) payload.get("participants");
            participants = participants == null ? new ArrayList<>() : participants;
            if (!participants.contains(principal.getName())) {
                participants.add(principal.getName());
            }
            ChatRoom createdRoom = chatService.createRoomWithParticipants(name, participants);
            return ResponseEntity.ok(createdRoom);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @MessageMapping("/chat.addUser/{roomId}")
    public void addUser(@DestinationVariable Long roomId,
                        @Payload ChatMessage chatMessage,
                        SimpMessageHeaderAccessor headerAccessor) {
        String username = chatMessage.getSender().getUsername();
        headerAccessor.getSessionAttributes().put("username", username);
        headerAccessor.getSessionAttributes().put("room_id", roomId);

        chatService.addUserToRoom(username, roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, chatMessage);
    }

    @MessageMapping("/chat.sendMessage/{roomId}")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload ChatMessage chatMessage) {
        // Check if base64 file data is present
        if (chatMessage.getFileData() != null && chatMessage.getFileData().length > 0) {
            try {
                // Decode base64 data
                byte[] decodedBytes = Base64.getDecoder().decode(new String(chatMessage.getFileData(), StandardCharsets.UTF_8));
                chatMessage.setFileData(decodedBytes);
            } catch (Exception e) {
                System.out.println("Error decoding file data: " + e.getMessage());
            }
        }
        ChatMessage savedMessage = chatService.saveMessage(chatMessage, roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, savedMessage);
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/chat/room/{roomId}")
    @ResponseBody
    public ResponseEntity<?> deleteRoom(@PathVariable Long roomId, Principal principal) {
        try {
            // Find participants before deleting
            List<String> participants = chatRoomRepository.findById(roomId)
                    .map(room -> room.getParticipants().stream()
                            .map(User::getUsername)
                            .collect(Collectors.toList()))
                    .orElse(Collections.emptyList());

            chatService.deleteRoom(roomId, principal.getName());

            // Broadcast room deletion to all participants
            for (String participant : participants) {
                messagingTemplate.convertAndSend("/topic/rooms/delete/" + participant, roomId);
            }

            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/chat/room/{roomId}")
    @ResponseBody
    public ChatRoom renameRoom(@PathVariable Long roomId,
                               @RequestBody Map<String, String> payload,
                               Principal principal) {
        String newName = payload.get("name");
        ChatRoom renamedRoom = chatService.renameRoom(roomId, newName, principal.getName());

        for (User participant : renamedRoom.getParticipants()) {
            messagingTemplate.convertAndSend("/topic/room/rename/" + participant.getUsername(), renamedRoom);
        }
        return renamedRoom;
    }

    @GetMapping("/messages/{messageId}/file")
    @ResponseBody
    public ResponseEntity<byte[]> getFile(@PathVariable Long messageId) {
        ChatMessage message = fileStorageService.getFile(messageId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + message.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(message.getFileType()))
                .body(message.getFileData());
    }
    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") Long roomId,
            Principal principal
    ) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large"));
        }
        try {
            String username = principal.getName();
            User sender = userService.findByUsername(username);
            ChatRoom room = chatRoomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            String fileName = StringUtils.cleanPath(file.getOriginalFilename());

            ChatMessage message = ChatMessage.builder()
                    .fileName(fileName)
                    .fileType(file.getContentType())
                    .fileData(file.getBytes())
                    .sender(sender)
                    .room(room)
                    .content("") // Provide an empty string
                    .type(MessageType.CHAT)
                    .timestamp(LocalDateTime.now())
                    .build();

            chatMessageRepository.save(message);

            return ResponseEntity.ok(Map.of(
                    "fileId", message.getId().toString(),
                    "fileName", fileName
            ));
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/api/messages/send")
    public ResponseEntity<ChatMessage> sendMessage(
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("roomId") Long roomId,
            Principal principal
    ) throws IOException {
        ChatMessage message = chatService.saveMessage(content, file, roomId, principal.getName());
        return ResponseEntity.ok(message);
    }

    // Add pagination for chat history
    @GetMapping("/chat/history/{roomId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = chatService.getRoomHistory(roomId, pageable);
        return ResponseEntity.ok(messages.getContent());
    }
}