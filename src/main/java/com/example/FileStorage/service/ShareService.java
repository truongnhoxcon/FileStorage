package com.example.FileStorage.service;

import com.example.FileStorage.entity.Share;
import com.example.FileStorage.repository.ShareRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ShareService {

    private final ShareRepository shareRepository;

    public ShareService(ShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public Share createShare(Share share) {
        return shareRepository.save(share);
    }

    public Optional<Share> getShareById(Long id) {
        return shareRepository.findById(id);
    }

    public Share getShareByLink(String link) {
        return shareRepository.findByShareLink(link);
    }

    public void deleteShare(Long id) {
        shareRepository.deleteById(id);
    }
}

