package com.example.app.repository;

import com.example.app.entity.APIKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface APIKeyRepository extends JpaRepository<APIKey, String> {
    List<APIKey> findByUserId(String userId);
    Optional<APIKey> findByKey(String key);
    void deleteByUserId(String userId);
}
