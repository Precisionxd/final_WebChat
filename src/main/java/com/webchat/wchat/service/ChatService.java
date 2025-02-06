package com.webchat.wchat.service;

import com.webchat.wchat.chat.MessageType;
import com.webchat.wchat.model.*;
import com.webchat.wchat.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatRoom getOrCreatePublicRoom() {
        return chatRoomRepository.findByName("Public Chat")
                .orElseGet(() -> {
                    ChatRoom publicRoom = new ChatRoom();
                    publicRoom.setName("Public Chat");
                    publicRoom.setParticipants(new ArrayList<>());
                    return chatRoomRepository.save(publicRoom);
                });
    }

    @Transactional
    public void deleteRoom(Long roomId, String username) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // Prevent deleting public room
        if ("Public Chat".equals(room.getName())) {
            throw new RuntimeException("Cannot delete public room");
        }

        // Verify user is a participant
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!room.getParticipants().contains(user)) {
            throw new RuntimeException("User is not a participant of this room");
        }

        chatRoomRepository.delete(room);
    }

    @Transactional
    public ChatRoom renameRoom(Long roomId, String newName, String username) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // Prevent renaming public room
        if ("Public Chat".equals(room.getName())) {
            throw new RuntimeException("Cannot rename public room");
        }

        // Verify user is a participant
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!room.getParticipants().contains(user)) {
            throw new RuntimeException("User is not a participant of this room");
        }

        room.setName(newName);
        return chatRoomRepository.save(room);
    }

    @Transactional
    public ChatMessage saveMessage(ChatMessage chatMessage, Long roomId) {
        User sender = userRepository.findByUsername(chatMessage.getSender().getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        chatMessage.setSender(sender);
        chatMessage.setRoom(room);
        chatMessage.setTimestamp(LocalDateTime.now());

        return chatMessageRepository.save(chatMessage);
    }

    @Transactional
    public ChatMessage saveMessage(String content, MultipartFile file, Long roomId, String username) throws IOException {
        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        ChatMessage message = new ChatMessage();
        message.setSender(sender);
        message.setRoom(room);
        message.setContent(content != null ? content : "");
        message.setTimestamp(LocalDateTime.now());
        message.setType(MessageType.CHAT);

        if (file != null && !file.isEmpty()) {
            message.setFileName(file.getOriginalFilename());
            message.setFileType(file.getContentType());
            message.setFileData(file.getBytes());
        }

        return chatMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public Page<ChatMessage> getRoomHistory(Long roomId, Pageable pageable) {
        return chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
    }

    public List<ChatRoom> getUserRooms(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getRooms();
    }

    @Transactional
    public void addUserToRoom(String username, Long roomId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        if (!room.getParticipants().contains(user)) {
            room.getParticipants().add(user);
            chatRoomRepository.save(room);
        }
    }

    @Transactional
    public ChatRoom createRoomWithParticipants(String name, List<String> participantUsernames) {
        if (chatRoomRepository.existsByName(name)) {
            throw new RuntimeException("Room name already exists");
        }

        ChatRoom room = new ChatRoom();
        room.setName(name);

        List<User> participants = userRepository.findByUsernameIn(participantUsernames);
        room.setParticipants(participants);

        ChatRoom savedRoom = chatRoomRepository.save(room);

        for (String username : participantUsernames) {
            messagingTemplate.convertAndSend("/topic/rooms/" + username, savedRoom);
        }

        return savedRoom;
    }
}