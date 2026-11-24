package com.theclimbingnight.push.repository;

import com.theclimbingnight.push.entity.PushTicket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTicketRepository extends JpaRepository<PushTicket, Long> {
}
