package com.webchat.wchat.service;

import com.webchat.wchat.model.ChatRoom;
import com.webchat.wchat.model.User;
import com.webchat.wchat.repository.ChatRoomRepository;
import com.webchat.wchat.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true, true, true,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Transactional
    public User registerNewUser(String username, String email, String password) {
        validateRegistrationInput(username, email, password);

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        addUserToPublicRoom(savedUser);
        return savedUser;
    }

    @Transactional
    public void addUserToPublicRoom(User user) {
        ChatRoom publicRoom = chatRoomRepository.findByName("Public Chat")
                .orElseGet(() -> {
                    ChatRoom room = new ChatRoom();
                    room.setName("Public Chat");
                    room.setParticipants(new ArrayList<>());
                    return chatRoomRepository.save(room);
                });

        if (!publicRoom.getParticipants().contains(user)) {
            publicRoom.getParticipants().add(user);
            chatRoomRepository.save(publicRoom);
        }
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional
    public List<String> findAllUsernames() {
        return userRepository.findAll().stream()
                .map(User::getUsername)
                .collect(Collectors.toList());
    }
//Future implementation
//    @Transactional
//    public void updateUser(User user) {
//        userRepository.save(user);
//    }
//
//    @Transactional
//    public void deleteUser(String username) {
//        User user = findByUsername(username);
//        userRepository.delete(user);
//    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = findByUsername(username);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void validateRegistrationInput(String username, String email, String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if (!Pattern.matches("^[A-Za-z0-9+_.-]+@(.+)$", email)) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }
}