package com.example.FileStorage.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FileManagerController {

    @GetMapping("/file-manager")
    public String fileManager() {
        return "forward:/file-manager.html";
    }
}
