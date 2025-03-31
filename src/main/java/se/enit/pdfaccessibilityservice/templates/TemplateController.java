package se.enit.pdfaccessibilityservice.templates;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private static final Logger logger = LoggerFactory.getLogger(TemplateController.class);

    @Autowired
    private TemplateRepository templateRepository;

    // POST endpoint to save a template
    @PostMapping
    public ResponseEntity<String> saveTemplate(@RequestBody Template template) {
        try {
            logger.info("Received request to save a template." + template.getJsonData() );

            // Parse jsonData to extract templateName if it's not provided directly
            if ((template.getTemplateName() == null || template.getTemplateName().isEmpty()) && template.getJsonData() != null) {
                JSONObject jsonData = new JSONObject(template.getJsonData());
                if (jsonData.has("templateName")) {
                    template.setTemplateName(jsonData.getString("templateName"));
                } else {
                    logger.warn("No templateName found in jsonData. Assigning default name 'Unnamed Template'.");
                    template.setTemplateName("Unnamed Template");
                }
            }

            logger.info("Template name after parsing: {}", template.getTemplateName());

            // Fetch all templates with the same templateName
            List<Template> existingTemplates = templateRepository.findByTemplateName(template.getTemplateName());

            // Determine new version number
            int newVersion = existingTemplates.stream()
                    .mapToInt(Template::getVersion)
                    .max()
                    .orElse(0) + 1;

            // Update all existing templates to set "latest" to false
            for (Template existingTemplate : existingTemplates) {
                existingTemplate.setLatest(false);
                templateRepository.save(existingTemplate);
                logger.info("Updated template with ID {} to latest=false", existingTemplate.getId());
            }

            // Save the new template with incremented version and "latest" flag
            template.setVersion(newVersion);
            template.setLatest(true);
            templateRepository.save(template);

            logger.info("Saved new version {} for template: {}", newVersion, template.getTemplateName());
            return ResponseEntity.ok("Template saved successfully with version: " + newVersion);
        } catch (Exception e) {
            logger.error("Error occurred while saving template: ", e);
            return ResponseEntity.status(500).body("Error saving template: " + e.getMessage());
        }
    }



    @GetMapping("/latest/{name}")
    public ResponseEntity<Template> getLatestTemplateByName(@PathVariable String name) {
        try {
            logger.info("Fetching latest template for name: {}", name);
            Template latestTemplate = templateRepository.findByTemplateName(name).stream()
                    .filter(Template::isLatest)
                    .findFirst()
                    .orElse(null);

            if (latestTemplate != null) {
                return ResponseEntity.ok(latestTemplate);
            } else {
                logger.warn("No template found with name: {}", name);
                return ResponseEntity.status(404).body(null);
            }
        } catch (Exception e) {
            logger.error("Error occurred while fetching latest template: ", e);
            return ResponseEntity.status(500).body(null);
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTemplateById(@PathVariable String id) {
        try {
            logger.info("Received request to delete template with ID: {}", id);

            // Check if the template exists
            if (templateRepository.existsById(id)) {
                templateRepository.deleteById(id);
                logger.info("Template with ID: {} successfully deleted.", id);
                return ResponseEntity.ok("Template successfully deleted!");
            } else {
                logger.warn("Template with ID: {} not found.", id);
                return ResponseEntity.status(404).body("Template not found!");
            }
        } catch (Exception e) {
            logger.error("Error occurred while deleting template with ID: {}", id, e);
            return ResponseEntity.status(500).body("Error deleting template: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> getAllTemplates() {
        try {
            logger.info("Fetching all templates");

            List<Map<String, String>> templates = templateRepository.findAll().stream()
                    .map(template -> Map.of(
                            "id", template.getId(),
                            "templateName", template.getTemplateName() != null ? template.getTemplateName() : "Unnamed Template",
                            "version", String.valueOf(template.getVersion()), // Convert version to String
                            "latest", String.valueOf(template.isLatest()) // Convert latest to String
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            logger.error("Error occurred while fetching templates: ", e);
            return ResponseEntity.status(500).body(Collections.emptyList());
        }
    }

    // GET endpoint to fetch a template by ID
    @GetMapping("/{id}")
    public ResponseEntity<Template> getTemplateById(@PathVariable String id) {
        return templateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(null));
    }
}
