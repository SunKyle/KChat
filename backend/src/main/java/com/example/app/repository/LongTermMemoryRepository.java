
package com.example.app.repository;

import com.example.app.entity.LongTermMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LongTermMemoryRepository extends JpaRepository<LongTermMemory, String> {
    List<LongTermMemory> findByUserIdOrderByCreatedAtDesc(String userId);
    List<LongTermMemory> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, String type);
    void deleteByUserId(String userId);
}
