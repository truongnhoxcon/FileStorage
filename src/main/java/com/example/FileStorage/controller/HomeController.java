package com.example.FileStorage.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "forward:/home.html";
    }

    @GetMapping("/home")
    public String homePage() {
        return "forward:/home.html";
    }
}
