package com.example.FileStorage.repository;

import com.example.FileStorage.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<Share, Long> {
    Share findByShareLink(String shareLink);
}