package com.jyfc.backend.module.agent.repository;

import com.jyfc.backend.module.agent.entity.AgentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, Long> {
}
