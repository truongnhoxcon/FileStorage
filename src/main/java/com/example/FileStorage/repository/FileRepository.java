package com.example.FileStorage.repository;

import com.example.FileStorage.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByUserId(Long userId);
    
    @Query("SELECT f FROM FileEntity f WHERE f.storagePath LIKE :directoryPath%")
    List<FileEntity> findByStoragePathStartingWith(@Param("directoryPath") String directoryPath);
}
