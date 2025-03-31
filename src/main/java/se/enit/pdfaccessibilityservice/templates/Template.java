package se.enit.pdfaccessibilityservice.templates;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import se.enit.pdfaccessibilityservice.TaggingInfo;

import java.util.List;

@Document(collection = "templates")
public class Template {

    @Id
    private String id;

    private String name;
    private String templateName; // New field
    private List<TaggingInfo> taggingInformation;
    private String pdfBlob;

    private String jsonData;
    private int version;
    private boolean latest;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }
    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public List<TaggingInfo> getTaggingInformation() {
        return taggingInformation;
    }

    public void setTaggingInformation(List<TaggingInfo> taggingInformation) {
        this.taggingInformation = taggingInformation;
    }

    public String getPdfBlob() {
        return pdfBlob;
    }

    public void setPdfBlob(String pdfBlob) {
        this.pdfBlob = pdfBlob;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isLatest() {
        return latest;
    }

    public void setLatest(boolean latest) {
        this.latest = latest;
    }

    @Override
    public String toString() {
        return "Template{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", templateName='" + templateName + '\'' +
                ", taggingInformation=" + taggingInformation +
                ", pdfBlob='" + pdfBlob + '\'' +
                ", version=" + version +
                ", latest=" + latest +
                '}';
    }
}