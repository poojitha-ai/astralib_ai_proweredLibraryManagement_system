package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PathwayBookRepository extends JpaRepository<PathwayBook, Long> {

    List<PathwayBook> findByBook_Id(Long bookId);

    List<PathwayBook> findByPathway_IdOrderByPositionAsc(Long pathwayId);
}
