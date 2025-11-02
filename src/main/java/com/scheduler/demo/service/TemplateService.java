package com.scheduler.demo.service;

import com.scheduler.demo.model.Template;
import com.scheduler.demo.repository.TemplateRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TemplateService {

    private final TemplateRepository templateRepository;

    public TemplateService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Cacheable(value = "templates", key = "#key")
    public Optional<String> getTemplateContent(String key) {
        return templateRepository.findByKeyName(key).map(Template::getContent);
    }
}

