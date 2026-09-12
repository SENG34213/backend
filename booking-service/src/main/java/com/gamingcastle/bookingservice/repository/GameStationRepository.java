package com.gamingcastle.bookingservice.repository;

import com.gamingcastle.bookingservice.entity.GameStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameStationRepository extends JpaRepository<GameStation, UUID> {
}
