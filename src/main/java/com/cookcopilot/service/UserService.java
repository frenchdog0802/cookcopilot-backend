package com.cookcopilot.service;

import com.cookcopilot.common.GlobalExceptionHandler.*;
import com.cookcopilot.entity.User;
import com.cookcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<User> listAll() {
        return userRepository.findAll();
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public User update(UUID id, User updates) {
        User user = findById(id);
        if (updates.getFirstName() != null) user.setFirstName(updates.getFirstName());
        if (updates.getLastName() != null) user.setLastName(updates.getLastName());
        if (updates.getName() != null) user.setName(updates.getName());
        if (updates.getEmail() != null) user.setEmail(updates.getEmail());
        if (updates.getPicture() != null) user.setPicture(updates.getPicture());
        return userRepository.save(user);
    }

    public void delete(UUID id) {
        User user = findById(id);
        userRepository.delete(user);
    }
}
