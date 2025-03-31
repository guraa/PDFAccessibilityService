package se.enit.pdfaccessibilityservice;

import com.itextpdf.io.exceptions.IOException;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.source.ByteArrayOutputStream;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.navigation.PdfDestination;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.*;

@Service
public class PdfService {
    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);


    private final TableProcessor tableProcessor = new TableProcessor();
    public byte[] processPdf(MultipartFile pdfFile, String tags) throws IOException, java.io.IOException {
        logger.info("Received tags: {}", tags);

        if (pdfFile.isEmpty()) {
            logger.error("No PDF file provided.");
            throw new IOException("No PDF file provided.");
        }



        logger.info("Received PDF file: {}", pdfFile.getOriginalFilename());
        logger.info("File size: {} bytes", pdfFile.getSize());

        // Parse input JSON
        JSONObject jsonObject = new JSONObject(tags);

        // Extract the taggingInformation array
        JSONArray taggedElements = jsonObject.getJSONArray("taggingInformation");

        Set<String> elementIds = new HashSet<>();
        for (int i = 0; i < taggedElements.length(); i++) {
            JSONObject element = taggedElements.getJSONObject(i);
            String id = element.optString("id", "");
            String type = element.getString("type");
            if (!id.isEmpty()) {
                if (elementIds.contains(id)) {
                    logger.warn("Duplicate element ID found: {}, type: {}", id, type);
                } else {
                    elementIds.add(id);
                }
            }
            logger.info("Element {}: ID={}, type={}", i, id, type);
        }

        // Filter and sort images (if applicable)
        JSONArray filteredImages = filterImages(taggedElements);
        JSONArray sortedImages = sortJsonByImageName(filteredImages);

        Map<Integer, List<ImageDataWithPosition>> pageImagesMap;

        // Convert InputStream to byte[]
        byte[] pdfBytes = pdfFile.getInputStream().readAllBytes();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(pdfFile.getInputStream());
            reader.setUnethicalReading(true);

            PdfWriter writer = new PdfWriter(byteArrayOutputStream, new WriterProperties().addXmpMetadata());
            PdfDocument inputPdfDocument = new PdfDocument(reader);
            PdfDocument outputPdfDocument = new PdfDocument(writer);

            outputPdfDocument.setTagged();
            outputPdfDocument.getCatalog().setLang(new PdfString("sv-SE"));
            outputPdfDocument.getCatalog().setViewerPreferences(new PdfViewerPreferences().setDisplayDocTitle(true));

            // Set PDF metadata (important for PDF/UA compliance)
            PdfDocumentInfo info = outputPdfDocument.getDocumentInfo();
            info.setTitle("Accessible PDF Document");
            info.setAuthor("Gustav Tullberg");
            info.setSubject("PDF/UA compliant document");

            // Copy pages and remove original content
            for (int i = 1; i <= inputPdfDocument.getNumberOfPages(); i++) {
                PdfPage inputPage = inputPdfDocument.getPage(i);

                // Copy the page to the output document
                PdfPage outputPage = inputPage.copyTo(outputPdfDocument);
                outputPdfDocument.addPage(outputPage);

                // Remove the original content
                removeOriginalContent(outputPage);
            }

            // Extract images using PDFBox and store them
            try (PDDocument pdfBoxDocument = Loader.loadPDF(pdfBytes)) {
                pageImagesMap = extractAndStoreImages(pdfBoxDocument, sortedImages);
            }

            PdfStructElem parentStructElem = new PdfStructElem(outputPdfDocument, PdfName.Document);
            outputPdfDocument.getStructTreeRoot().addKid(parentStructElem);

            try (Document document = new Document(outputPdfDocument)) {
                // Create bookmarks for the document
                Map<String, PdfOutline> bookmarks = createBookmarks(outputPdfDocument, taggedElements);

                // Reinsert and tag images
                reinsertAndTagImages(outputPdfDocument, pageImagesMap, document, sortedImages, bookmarks);

                // Process text and table elements
                for (int i = 0; i < taggedElements.length(); i++) {
                    JSONObject element = taggedElements.getJSONObject(i);
                    String type = element.getString("type");

                    if (type.equalsIgnoreCase("text")) {
                        extractAndReinsertText(inputPdfDocument, outputPdfDocument, element, parentStructElem, document, bookmarks);
                    } else if (type.equalsIgnoreCase("table")) {
                        // Convert JSONObject to TaggingInfo for table processing
                        se.enit.pdfaccessibilityservice.TaggingInfo tableInfo = convertJsonToTaggingInfo(element);
                        logger.info("Starting to process element {} of type {}", i, element.getString("type"));

                        tableProcessor.processTable(inputPdfDocument, outputPdfDocument, tableInfo,
                                parentStructElem, document, bookmarks);
                    }
                }

                logPdfDetails(outputPdfDocument);

                inputPdfDocument.close();
                document.close();
                outputPdfDocument.close();

                logger.info("PDF processing complete. Returning the generated PDF.");
                return byteArrayOutputStream.toByteArray();
            }
        }
    }


    private se.enit.pdfaccessibilityservice.TaggingInfo convertJsonToTaggingInfo(JSONObject element) {
        se.enit.pdfaccessibilityservice.TaggingInfo info = new se.enit.pdfaccessibilityservice.TaggingInfo();

        // Set basic properties
        info.setType(element.optString("type"));
        info.setName(element.optString("name"));
        info.setTag(element.optString("tag"));
        info.setLanguage(element.optString("language"));
        info.setFont(element.optString("font"));
        info.setX(element.optDouble("x"));
        info.setY(element.optDouble("y"));
        info.setWidth(element.optDouble("width"));
        info.setHeight(element.optDouble("height"));
        info.setPage(element.optInt("page"));
        info.setAlt(element.optString("alt"));
        info.setId(element.optString("id"));
        info.setSection(element.optString("section"));
        info.setArtifact(element.optBoolean("isArtifact"));
        info.setContainsTable(element.optBoolean("containsTable"));

        // Set table-specific properties
        if (info.isContainsTable()) {
            info.setRowCount(element.optInt("rowCount"));
            info.setColCount(element.optInt("colCount"));

            // Parse row and column positions
            if (element.has("rowPositions")) {
                JSONArray rowPositionsArray = element.getJSONArray("rowPositions");
                List<Float> rowPositions = new ArrayList<>();
                for (int i = 0; i < rowPositionsArray.length(); i++) {
                    rowPositions.add(rowPositionsArray.getFloat(i));
                }
                info.setRowPositions(rowPositions);
            }

            if (element.has("colPositions")) {
                JSONArray colPositionsArray = element.getJSONArray("colPositions");
                List<Float> colPositions = new ArrayList<>();
                for (int i = 0; i < colPositionsArray.length(); i++) {
                    colPositions.add(colPositionsArray.getFloat(i));
                }
                info.setColPositions(colPositions);
            }

            // Set header row/column info
            if (!element.isNull("headerRow")) {
                info.setHeaderRow(element.optInt("headerRow"));
            }

            if (!element.isNull("headerCol")) {
                info.setHeaderCol(element.optInt("headerCol"));
            }

            // Parse WCAG data if available
            if (element.has("wcagData")) {
                JSONObject wcagDataJson = element.getJSONObject("wcagData");
                se.enit.pdfaccessibilityservice.WcagTableData wcagData = new se.enit.pdfaccessibilityservice.WcagTableData();

                wcagData.setHasHeader(wcagDataJson.optBoolean("hasHeader"));
                wcagData.setSummary(wcagDataJson.optString("summary"));
                wcagData.setCaption(wcagDataJson.optString("caption"));
                wcagData.setScope(wcagDataJson.optString("scope"));
                wcagData.setComplex(wcagDataJson.optBoolean("isComplex"));

                // Parse header rows
                if (wcagDataJson.has("headerRows")) {
                    JSONArray headerRowsArray = wcagDataJson.getJSONArray("headerRows");
                    List<Integer> headerRows = new ArrayList<>();
                    for (int i = 0; i < headerRowsArray.length(); i++) {
                        headerRows.add(headerRowsArray.getInt(i));
                    }
                    wcagData.setHeaderRows(headerRows);
                }

                // Parse header columns
                if (wcagDataJson.has("headerCols")) {
                    JSONArray headerColsArray = wcagDataJson.getJSONArray("headerCols");
                    List<Integer> headerCols = new ArrayList<>();
                    for (int i = 0; i < headerColsArray.length(); i++) {
                        headerCols.add(headerColsArray.getInt(i));
                    }
                    wcagData.setHeaderCols(headerCols);
                }

                info.setWcagData(wcagData);
            }
        }

        return info;
    }
    private void removeOriginalContent(PdfPage page) {
        try {
            // Get all content streams and clear them all
            int streamCount = page.getContentStreamCount();
            logger.info("Page has {} content streams", streamCount);

            for (int i = 0; i < streamCount; i++) {
                PdfStream contentStream = page.getContentStream(i);
                if (contentStream != null) {
                    contentStream.setData(new byte[0]);
                    logger.info("Cleared content stream {}", i);
                }
            }

            // Ensure resources are also properly cleaned
            PdfDictionary resources = page.getResources().getPdfObject();
            if (resources != null) {
                // Clear form XObjects which might contain content
                PdfDictionary xobjects = resources.getAsDictionary(PdfName.XObject);
                if (xobjects != null) {
                    xobjects.clear();
                    logger.info("Cleared XObjects from resources");
                }
            }


            logger.info("Cleared all content from page");
        } catch (Exception e) {
            logger.error("Error clearing page content", e);
        }
    }



    public void reinsertAndTagImages(PdfDocument pdfDocument,
                                     Map<Integer, List<ImageDataWithPosition>> pageImagesMap,
                                     Document document, JSONArray tags,  Map<String, PdfOutline> bookmarks) {

        float cmToPoints = 28.3465f; // Conversion from cm to points

        for (int i = 0; i < tags.length(); i++) {
            JSONObject tagElement = tags.getJSONObject(i);

            if ("image".equalsIgnoreCase(tagElement.getString("type"))) {
                int pageNumber = tagElement.optInt("page", 1) - 1;
                float x = (float) tagElement.optDouble("x", 0) * cmToPoints;
                float y = (float) tagElement.optDouble("y", 0) * cmToPoints;
                float width = (float) tagElement.optDouble("width", 100) * cmToPoints;
                float height = (float) tagElement.optDouble("height", 12) * cmToPoints;
                String altText = tagElement.optString("alt", "Accessible Image");

                String id = tagElement.optString("id");
                PdfOutline bookmark = bookmarks.get(id);

                if (bookmark != null) {
                    PdfPage pdfPage = pdfDocument.getPage(pageNumber + 1);
                    float adjustedY = pdfPage.getPageSize().getHeight() - y;

                    PdfDestination destination = PdfExplicitDestination.createXYZ(pdfPage, x, adjustedY, 1);
                    bookmark.addDestination(destination);

                    logger.info("Linked bookmark for image ID: " + id + " on page: " + (pageNumber + 1));
                }

                if (pageImagesMap.containsKey(pageNumber)) {
                    List<ImageDataWithPosition> imagesOnPage = pageImagesMap.get(pageNumber);
                    if (i < imagesOnPage.size()) {
                        ImageDataWithPosition imageData = imagesOnPage.get(i);
                        PdfPage pdfPage = pdfDocument.getPage(pageNumber + 1);

                        float pageHeight = pdfPage.getPageSize().getHeight();
                        float adjustedY = pageHeight - y; // Flip coordinate system for iText

                        // Create structure element for tagging the image
                        PdfStructElem structElem = new PdfStructElem(pdfDocument, PdfName.Figure);
                        structElem.put(PdfName.Alt, new PdfString(altText));

                        // Flip the image vertically
                        Image image = new Image(imageData.getImage())
                                .setFixedPosition(pageNumber + 1, x, adjustedY) // Adjust for top-left origin
                                .scaleAbsolute(-width, height); // Scale to fit width/height

                        // Apply the flipping transformation
                        image.setRotationAngle(Math.PI); // Rotate 180 degrees to flip vertically

                        // Set accessibility properties for the image
                        image.getAccessibilityProperties().setRole("Figure");
                        image.getAccessibilityProperties().setAlternateDescription(altText);

                        // Add the image to the document
                        document.add(image);

                        logger.info("Reinserted and tagged vertically flipped image on page " + (pageNumber + 1));
                    } else {
                        logger.warn("No image found for tag " + i + " on page " + (pageNumber + 1));
                    }
                } else {
                    logger.warn("No images mapped for page " + (pageNumber + 1));
                }
            }
        }
    }


    private JSONArray filterImages(JSONArray tags) {
        JSONArray filteredImages = new JSONArray();

        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.getJSONObject(i);
            if ("image".equalsIgnoreCase(tag.optString("type", ""))) {
                filteredImages.put(tag);
            }
        }

        return filteredImages;
    }

    private JSONArray sortJsonByImageName(JSONArray tags) {
        List<JSONObject> jsonList = new ArrayList<>();
        for (int i = 0; i < tags.length(); i++) {
            jsonList.add(tags.getJSONObject(i));
        }

        jsonList.sort((o1, o2) -> o1.optString("name", "").compareTo(o2.optString("name", "")));

        JSONArray sortedArray = new JSONArray();
        for (JSONObject jsonObject : jsonList) {
            sortedArray.put(jsonObject);
        }

        return sortedArray;
    }



    private Map<Integer, List<ImageDataWithPosition>> extractAndStoreImages(PDDocument document, JSONArray tags) {
        Map<Integer, List<ImageDataWithPosition>> pageImagesMap = new HashMap<>();
        int pageIndex = 0;

        for (PDPage page : document.getPages()) {
            List<ImageDataWithPosition> imageList = new ArrayList<>();
            PDResources resources = page.getResources();
            float pageHeight = page.getMediaBox().getHeight();

            for (COSName cosName : resources.getXObjectNames()) {
                try {
                    PDXObject xObject = resources.getXObject(cosName);
                    if (xObject instanceof PDImageXObject) {
                        PDImageXObject pdImageXObject = (PDImageXObject) xObject;

                        BufferedImage bufferedImage = pdImageXObject.getImage();
                        if (bufferedImage == null) {
                            logger.warn("Image extraction failed for COSName: " + cosName.getName());
                            continue;
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(bufferedImage, "png", baos);
                        byte[] imageBytes = baos.toByteArray();

                        ImageData imageData = ImageDataFactory.create(imageBytes);
                        PdfImageXObject pdfImageXObject = new PdfImageXObject(imageData);

                        // Flip Y-coordinate
                        Rectangle position = new Rectangle(
                                0,
                                pageHeight - pdfImageXObject.getHeight(),
                                pdfImageXObject.getWidth(),
                                pdfImageXObject.getHeight()
                        );

                        // Match JSON tag by page and position (or another unique property like ID)
                        String matchingId = findMatchingId(tags, pageIndex + 1, pdfImageXObject);
                        if (matchingId != null) {
                            ImageDataWithPosition imageDataWithPosition = new ImageDataWithPosition(
                                    pdfImageXObject,
                                    pageIndex + 1,
                                    position,
                                    matchingId
                            );
                            imageList.add(imageDataWithPosition);
                            logger.info("Extracted image matched with ID: " + matchingId + " on page " + (pageIndex + 1));
                        } else {
                            logger.warn("No matching JSON tag found for extracted image on page " + (pageIndex + 1));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to extract image", e);
                }
            }
            pageImagesMap.put(pageIndex, imageList);
            pageIndex++;
        }
        return pageImagesMap;
    }

    private String findMatchingId(JSONArray tags, int pageNumber, PdfImageXObject pdfImageXObject) {
        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.getJSONObject(i);
            if (tag.getInt("page") == pageNumber) {
                // Add further logic to match by dimensions, position, or other criteria
                return tag.getString("id");
            }
        }
        return null;
    }



    private void logPdfDetails(PdfDocument pdfDocument) {
        // Log document language
        PdfString lang = pdfDocument.getCatalog().getLang();
        if (lang != null) {
            logger.info("Document language: " + lang.toString());
        } else {
            logger.warn("No document language set.");
        }

        // Log structure tree root and its tags
        PdfStructTreeRoot structTreeRoot = pdfDocument.getStructTreeRoot();
        if (structTreeRoot == null) {
            logger.error("No structure tree root found. The PDF is not tagged.");
            return;
        }

        logger.info("Starting PDF tag structure log...");
        try {
            List<IStructureNode> rootKids = structTreeRoot.getKids();
            if (rootKids != null) {
                logStructureElements(rootKids, 0);
            } else {
                logger.warn("No tagged elements found in the structure tree.");
            }
        } catch (Exception e) {
            logger.error("Error while logging PDF structure: " + e.getMessage());
        }

        // Log fonts
        logPdfFonts(pdfDocument);
    }



    private void logStructureElements(List<IStructureNode> elements, int level) {
        String indent = "  ".repeat(level);  // Add indentation based on depth in structure tree
        for (IStructureNode node : elements) {
            if (node instanceof PdfStructElem) {
                PdfStructElem elem = (PdfStructElem) node;
                //logger.info(indent + "Tag: " + elem.getRole());
                PdfString actualText = elem.getPdfObject().getAsString(PdfName.ActualText);
                if (actualText != null) {
                    //logger.info(indent + "  ActualText: " + actualText.toString());
                }

                // Recursively log the children (if any)
                List<IStructureNode> kids = elem.getKids();
                if (kids != null && !kids.isEmpty()) {
                    logStructureElements(kids, level + 1);
                }
            }
        }
    }

    // Log all fonts used in the document
    private void logPdfFonts(PdfDocument pdfDocument) {
        try {
            for (int i = 1; i <= pdfDocument.getNumberOfPages(); i++) {
                PdfDictionary resources = pdfDocument.getPage(i).getResources().getPdfObject();
                PdfDictionary fonts = resources.getAsDictionary(PdfName.Font);

                if (fonts != null) {
                    for (PdfName fontName : fonts.keySet()) {
                        PdfDictionary fontDict = fonts.getAsDictionary(fontName);
                       // logger.info("Font found on page " + i + ": " + fontName.getValue());
                    }
                } else {
                 //   logger.warn("No fonts found on page " + i + ".");
                }
            }
        } catch (Exception e) {
          //  logger.error("Error logging fonts: " + e.getMessage());
        }
    }


    private Map<String, PdfOutline> createBookmarks(PdfDocument pdfDocument, JSONArray elements) {
        PdfOutline rootOutline = pdfDocument.getOutlines(false);
        Map<String, PdfOutline> bookmarks = new HashMap<>();

        Map<String, List<JSONObject>> groupedElements = groupElementsBySection(elements);

        groupedElements.forEach((section, items) -> {
            PdfOutline sectionOutline = rootOutline.addOutline(section);
            bookmarks.put(section, sectionOutline);

            for (JSONObject element : items) {
                String id = element.optString("id", UUID.randomUUID().toString());
                PdfOutline elementBookmark = sectionOutline.addOutline(id);
                bookmarks.put(id, elementBookmark);
            }
        });

        return bookmarks;
    }

    private Map<String, List<JSONObject>> groupElementsBySection(JSONArray elements) {
        Map<String, List<JSONObject>> sectionMap = new HashMap<>();

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            String section = element.optString("section", "Default"); // Default if section is null or empty

            // Add the element to the corresponding section list
            sectionMap.computeIfAbsent(section, k -> new ArrayList<>()).add(element);
        }

        return sectionMap;
    }

    private void extractAndReinsertText(PdfDocument inputPdfDocument, PdfDocument outputPdfDocument, JSONObject element, PdfStructElem parentStructElem, Document document,  Map<String, PdfOutline> bookmarks) throws IOException, java.io.IOException {
        float cmToPoints = 28.3465f;  // Conversion from cm to points
        int pageNumber = element.optInt("page", 1);

        // Use JSON for extraction but not insertion
        float x = (float) element.optDouble("x", 0) * cmToPoints;
        float y = (float) element.optDouble("y", 0) * cmToPoints;
        float width = (float) element.optDouble("width", 100) * cmToPoints;
        float height = (float) element.optDouble("height", 12) * cmToPoints;
        String tag = element.optString("tag", "P").trim();


        String id = element.optString("id");
        PdfOutline bookmark = bookmarks.get(id);



        logger.info("Processing text extraction for page: " + pageNumber);

        PdfPage inputPage = inputPdfDocument.getPage(pageNumber);
        PdfPage outputPage = outputPdfDocument.getPage(pageNumber);

        float pageHeight = inputPage.getPageSize().getHeight();

        // Adjust coordinates for PDF origin (bottom-left)
        Rectangle extractionRegion = new Rectangle(x, pageHeight - y - height, width, height);
        CustomTextRenderListener listener = new CustomTextRenderListener(extractionRegion);

        PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
        processor.processPageContent(inputPage);

        // Preprocess Y-coordinates to detect line breaks
        listener.preprocessYCoordinates();

        // Extracted text and style
        String extractedText = listener.getCleanExtractedText();
        String fontName = listener.getExtractedFontName();
        float fontSize = listener.getExtractedFontSize();
        Color fontColor = listener.getExtractedFontColor();
        Rectangle combinedBoundingBox = calculateCombinedBoundingBox(listener.getTextWithPositionList());

        // Log canonical format
        logger.info("Canonical Text for Extraction: '" + extractedText);
        logger.info("Canonical Text for Extraction: '" + extractedText.replace("\n", "\\n") + "'");
        logger.info("Bounding Box for Extraction: " + combinedBoundingBox);

        float adjustedY = combinedBoundingBox.getY();
        float exactX = combinedBoundingBox.getX();
        float exactWidth = combinedBoundingBox.getWidth();

        if (!extractedText.isEmpty()) {
            // Erase old text by drawing a white rectangle over the area
            PdfCanvas canvas = new PdfCanvas(outputPage);
            canvas.saveState();
            canvas.beginMarkedContent(PdfName.Artifact);
            canvas.setFillColor(ColorConstants.WHITE);
            canvas.rectangle(exactX, adjustedY, exactWidth, combinedBoundingBox.getHeight());
            canvas.fill();
            canvas.endMarkedContent();
            canvas.restoreState();

            // Reinsert text with extracted font properties
            PdfFont font;
            try {
                font = resolveFont(element.optString("font", ""));
            } catch (IOException e) {
                logger.warn("Font not recognized, using Helvetica as fallback.");
                font = PdfFontFactory.createFont(StandardFonts.HELVETICA, PdfEncodings.IDENTITY_H);
            }

            if (bookmark != null) {
                PdfDestination destination = PdfExplicitDestination.createXYZ(outputPage, exactX, adjustedY, 1);
                bookmark.addDestination(destination);

                logger.info("Linked bookmark for text ID: " + id + " on page: " + pageNumber);
            }


            Paragraph p = new Paragraph(extractedText)
                    .setFixedPosition(exactX, adjustedY, exactWidth)
                    .setFont(font)
                    .setFontSize(fontSize)
                    .setFontColor(fontColor);

            p.getAccessibilityProperties().setRole(tag);

            document.add(p);



            logger.info("Reinserted and tagged text as " + tag + " on page " + pageNumber);
        } else {
            logger.warn("No text found in the specified region on page " + pageNumber);
        }
    }


    // Utility method to calculate combined bounding box
// Utility method to combine two rectangles
    private Rectangle combineBoundingBoxes(Rectangle r1, Rectangle r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;

        float x = Math.min(r1.getX(), r2.getX());
        float y = Math.min(r1.getY(), r2.getY());
        float width = Math.max(r1.getX() + r1.getWidth(), r2.getX() + r2.getWidth()) - x;
        float height = Math.max(r1.getY() + r1.getHeight(), r2.getY() + r2.getHeight()) - y;

        return new Rectangle(x, y, width, height);
    }

    // Updated method to calculate the overall bounding box of extracted text
    private Rectangle calculateCombinedBoundingBox(List<CustomTextRenderListener.TextWithPosition> textWithPositionList) {
        if (textWithPositionList.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }

        Rectangle combinedBoundingBox = textWithPositionList.get(0).getBoundingBox();

        for (CustomTextRenderListener.TextWithPosition textPosition : textWithPositionList) {
            combinedBoundingBox = combineBoundingBoxes(combinedBoundingBox, textPosition.getBoundingBox());
        }
        return combinedBoundingBox;
    }


    /**
     * Resolves font from Base64 or defaults to Helvetica if unavailable.
     */
    private PdfFont resolveFont(String base64Font) throws IOException, java.io.IOException {
        if (base64Font.isEmpty()) {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA, PdfEncodings.IDENTITY_H);
        }
        try {
            byte[] fontBytes = Base64.getDecoder().decode(base64Font);
            FontProgram fontProgram = FontProgramFactory.createFont(fontBytes);
            PdfFont pdfFont = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

            // Retrieve the font descriptor
            PdfDictionary fontDescriptor = pdfFont.getPdfObject().getAsDictionary(PdfName.FontDescriptor);
            if (fontDescriptor != null) {
                // Generate and embed the CIDSet stream
                byte[] cidSet = generateCIDSet(fontProgram);
                PdfStream cidSetStream = new PdfStream(cidSet);
                fontDescriptor.put(PdfName.CIDSet, cidSetStream);
                logger.info("Embedded CIDSet with {} bytes", cidSet.length);
            }

            return pdfFont;

        } catch (IOException e) {
            logger.warn("Font resolution failed, using fallback font. Error: {}", e.getMessage());
            return PdfFontFactory.createFont(StandardFonts.HELVETICA, PdfEncodings.IDENTITY_H);
        }
    }

    // Generate the CIDSet byte array
    private byte[] generateCIDSet(FontProgram fontProgram) {
        Set<Integer> glyphsUsed = new HashSet<>();

        // Iterate through possible CIDs (0-65535 for CID fonts)
        for (int cid = 0; cid < 65536; cid++) {
            if (fontProgram.getGlyph(cid) != null) {
                glyphsUsed.add(cid);
            }
        }

        // Calculate size of CIDSet
        int totalGlyphs = glyphsUsed.size();
        byte[] cidSet = new byte[(totalGlyphs + 7) / 8];

        // Mark used glyphs
        for (int cid : glyphsUsed) {
            cidSet[cid / 8] |= (1 << (7 - (cid % 8)));
        }

        logger.info("Generated CIDSet with {} glyphs marked.", totalGlyphs);
        return cidSet;
    }

    // Method to add named destinations
    private void addNamedDestination(PdfDocument pdfDocument, String name, PdfDestination destination) {
        PdfCatalog catalog = pdfDocument.getCatalog();
        PdfDictionary namesDict = catalog.getPdfObject().getAsDictionary(PdfName.Names);
        if (namesDict == null) {
            namesDict = new PdfDictionary();
            catalog.getPdfObject().put(PdfName.Names, namesDict);
        }

        PdfDictionary destDict = namesDict.getAsDictionary(PdfName.Dests);
        if (destDict == null) {
            destDict = new PdfDictionary();
            namesDict.put(PdfName.Dests, destDict);
        }

        PdfArray destsArray = destDict.getAsArray(PdfName.Names);
        if (destsArray == null) {
            destsArray = new PdfArray();
            destDict.put(PdfName.Names, destsArray);
        }

        // Now that destsArray is guaranteed to be a PdfArray, add the destination
        destsArray.add(new PdfString(name));
        destsArray.add(destination.getPdfObject()); // destination.getPdfObject() is correct here
    }
}