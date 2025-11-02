package com.scheduler.demo.repository;

import com.scheduler.demo.model.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    Optional<Template> findByKeyName(String keyName);
}

