package com.edu.service;

import com.edu.common.JwtUtils;
import com.edu.domain.Role;
import com.edu.domain.User;
import com.edu.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void login_Success() {
        String username = "testuser";
        String password = "password";
        User user = new User();
        user.setUsername(username);
        user.setPassword("encodedPassword");
        user.setRole(Role.STUDENT);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(jwtUtils.generateToken(username, "STUDENT")).thenReturn("mockToken");

        Map<String, Object> result = authService.login(username, password, "STUDENT");

        assertNotNull(result);
        assertEquals("mockToken", result.get("token"));
        assertEquals(user, result.get("user"));
    }

    @Test
    void login_UserNotFound() {
        String username = "nonexistent";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.login(username, "password", "STUDENT"));
    }
}
