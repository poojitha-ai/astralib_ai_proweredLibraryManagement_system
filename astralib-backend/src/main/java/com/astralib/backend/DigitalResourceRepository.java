package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DigitalResourceRepository extends JpaRepository<DigitalResource, Long> {
    List<DigitalResource> findByBook_Id(Long bookId);
}
