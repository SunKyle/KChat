package com.example.app.repository;

import com.example.app.entity.TtsSpeaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TtsSpeakerRepository extends JpaRepository<TtsSpeaker, String> {

    List<TtsSpeaker> findByOwnerUserId(String ownerUserId);

    Optional<TtsSpeaker> findBySpkIdAndOwnerUserId(String spkId, String ownerUserId);

    boolean existsBySpkId(String spkId);
}
