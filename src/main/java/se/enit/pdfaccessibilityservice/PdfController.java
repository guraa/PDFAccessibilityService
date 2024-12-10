package se.enit.pdfaccessibilityservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/create-accessible-pdf")
public class PdfController {

    private static final Logger logger = LoggerFactory.getLogger(PdfController.class);
    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @PostMapping
    public ResponseEntity<byte[]> createAccessiblePdf(
            @RequestParam("pdf") MultipartFile pdfFile,
            @RequestParam("tags") String tags,
            @RequestParam(value = "images", required = false) MultipartFile[] images) {

        logger.info("Received request to create accessible PDF");
        try {
            byte[] pdfBytes = pdfService.processPdf(pdfFile, tags);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=accessible_output.pdf")
                    .body(pdfBytes);
        } catch (IOException e) {
            logger.error("Error while creating accessible PDF", e);
            return ResponseEntity.status(500).body(null);
        }
    }
}
