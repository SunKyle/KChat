package com.example.app.service;

import com.example.app.dto.MemoryDTO;

import java.util.List;

public interface MemoryRecaller {

    List<MemoryDTO> recall(String userId, String query, int topK);

    List<MemoryDTO> recall(String userId, String query, int topK, List<String> types);
}