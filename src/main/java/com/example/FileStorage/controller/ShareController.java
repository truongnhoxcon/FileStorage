package com.example.FileStorage.controller;

import com.example.FileStorage.entity.Share;
import com.example.FileStorage.service.ShareService;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public Share createShare(@RequestBody Share share) {
        return shareService.createShare(share);
    }

    @GetMapping("/{id}")
    public Optional<Share> getShareById(@PathVariable Long id) {
        return shareService.getShareById(id);
    }

    @GetMapping("/link/{link}")
    public Share getShareByLink(@PathVariable String link) {
        return shareService.getShareByLink(link);
    }

    @DeleteMapping("/{id}")
    public void deleteShare(@PathVariable Long id) {
        shareService.deleteShare(id);
    }
}
