package com.example.app.repository;

import com.example.app.entity.LongTermMemory;
import com.example.app.entity.LongTermMemory.MemoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LongTermMemoryRepository extends JpaRepository<LongTermMemory, Long> {

    List<LongTermMemory> findByUserId(String userId);

    List<LongTermMemory> findByUserIdOrderByCreatedAtDesc(String userId);

    List<LongTermMemory> findByUserIdAndType(String userId, MemoryType type);

    List<LongTermMemory> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, MemoryType type);

    List<LongTermMemory> findByUserIdAndImportanceGreaterThanEqual(String userId, Integer importance);

    List<LongTermMemory> findByExpiresAtBefore(LocalDateTime dateTime);

    List<LongTermMemory> findByUserIdAndExpiresAtBefore(String userId, LocalDateTime dateTime);

    @Query("SELECT m FROM LongTermMemory m WHERE m.userId = :userId AND m.type IN :types ORDER BY m.createdAt DESC")
    List<LongTermMemory> findByUserIdAndTypes(@Param("userId") String userId, @Param("types") List<MemoryType> types);

    @Query("SELECT m FROM LongTermMemory m WHERE m.userId = :userId AND m.importance >= :minImportance ORDER BY m.importance DESC, m.createdAt DESC")
    List<LongTermMemory> findByUserIdAndMinImportance(@Param("userId") String userId, @Param("minImportance") Integer minImportance);

    @Query("SELECT DISTINCT m.type FROM LongTermMemory m WHERE m.userId = :userId")
    List<MemoryType> findDistinctTypesByUserId(String userId);

    Optional<LongTermMemory> findByUserIdAndContent(String userId, String content);

    void deleteByUserId(String userId);

    int deleteByExpiresAtBefore(LocalDateTime dateTime);

    int countByUserId(String userId);

    int countByUserIdAndType(String userId, MemoryType type);
}