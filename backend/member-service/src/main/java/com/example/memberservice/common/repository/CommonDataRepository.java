package com.example.memberservice.common.repository;

import com.example.memberservice.common.entity.CommonData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommonDataRepository extends JpaRepository<CommonData, String> {
}
