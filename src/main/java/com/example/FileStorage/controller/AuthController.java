package com.example.FileStorage.controller;

import com.example.FileStorage.dto.LoginRequest;
import com.example.FileStorage.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthController(JwtService jwtService, AuthenticationManager authenticationManager) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            String token = jwtService.generateToken(request.getUsername());
            return ResponseEntity.ok(Map.of("token", token));
        } catch (AuthenticationException ex) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Tài khoản hoặc mật khẩu không đúng");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}


