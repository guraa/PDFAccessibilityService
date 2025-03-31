package se.enit.pdfaccessibilityservice.templates;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TemplateRepository extends MongoRepository<Template, String> {
    List<Template> findByTemplateName(String templateName); // Correctly match the field name
}
