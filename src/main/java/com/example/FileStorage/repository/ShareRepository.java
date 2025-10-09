package com.example.FileStorage.repository;

import com.example.FileStorage.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ShareRepository extends JpaRepository<Share, Long> {
    Share findByShareLink(String shareLink);

    @Query("SELECT s FROM Share s WHERE s.file.id = :fileId")
    List<Share> findByFileId(@Param("fileId") Long fileId);

    @Query("SELECT s FROM Share s WHERE s.recipient.id = :recipientId")
    List<Share> findByRecipientId(@Param("recipientId") Long recipientId);

    @Query("SELECT s FROM Share s WHERE s.file.id = :fileId AND s.recipient.id = :recipientId")
    Share findByFileIdAndRecipientId(@Param("fileId") Long fileId, @Param("recipientId") Long recipientId);
}