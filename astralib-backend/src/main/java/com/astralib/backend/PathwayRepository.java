package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PathwayRepository extends JpaRepository<Pathway, Long> {

    Optional<Pathway> findBySlug(String slug);

    List<Pathway> findAllByOrderByTypeAscNameAsc();
}
