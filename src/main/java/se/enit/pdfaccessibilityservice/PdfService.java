package se.enit.pdfaccessibilityservice;

import com.itextpdf.io.exceptions.IOException;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.source.ByteArrayOutputStream;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.navigation.PdfDestination;
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


    public byte[] processPdf(MultipartFile pdfFile, String tags) throws IOException, java.io.IOException {
        logger.info(tags);
        if (pdfFile.isEmpty()) {
            logger.error("No PDF file provided.");
            throw new IOException("No PDF file provided.");
        }

        logger.info("Received PDF file: " + pdfFile.getOriginalFilename());
        logger.info("File size: " + pdfFile.getSize() + " bytes");

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

            inputPdfDocument.copyPagesTo(1, inputPdfDocument.getNumberOfPages(), outputPdfDocument);

            JSONArray taggedElements = new JSONArray(tags);

            // Extract images using PDFBox and store them
            try (PDDocument pdfBoxDocument = Loader.loadPDF(pdfBytes)) {
                pageImagesMap = extractAndStoreImages(pdfBoxDocument, taggedElements);
            }

            PdfStructElem parentStructElem = new PdfStructElem(outputPdfDocument, PdfName.Document);
            outputPdfDocument.getStructTreeRoot().addKid(parentStructElem);

            try (Document document = new Document(outputPdfDocument)) {
                reinsertAndTagImages(outputPdfDocument, pageImagesMap, document, taggedElements);

                // Process text elements
                for (int i = 0; i < taggedElements.length(); i++) {
                    JSONObject element = taggedElements.getJSONObject(i);
                    String type = element.getString("type");

                    if (type.equalsIgnoreCase("text")) {
                        extractAndReinsertText(inputPdfDocument, outputPdfDocument, element, parentStructElem, document);
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

    public void reinsertAndTagImages(PdfDocument pdfDocument, Map<Integer, List<ImageDataWithPosition>> pageImagesMap, Document document, JSONArray tags) {
        float cmToPoints = 28.3465f;  // Conversion factor from cm to points

        for (int i = 0; i < tags.length(); i++) {
            JSONObject tagElement = tags.getJSONObject(i);

            if ("image".equalsIgnoreCase(tagElement.getString("type"))) {
                int pageNumber = tagElement.optInt("page", 1) - 1;
                float x = (float) tagElement.optDouble("x", 0) * cmToPoints;
                float y = (float) tagElement.optDouble("y", 0) * cmToPoints;
                float width = (float) tagElement.optDouble("width", 100) * cmToPoints;
                float height = (float) tagElement.optDouble("height", 12) * cmToPoints;
                String altText = tagElement.optString("alt", "Image");

                if (pageImagesMap.containsKey(pageNumber)) {
                    List<ImageDataWithPosition> imagesOnPage = pageImagesMap.get(pageNumber);
                    if (i < imagesOnPage.size()) {
                        ImageDataWithPosition imageData = imagesOnPage.get(i);
                        PdfPage pdfPage = pdfDocument.getPage(pageNumber + 1);

                        float pageHeight = pdfPage.getPageSize().getHeight();
                        float adjustedY = pageHeight - y;

                        // Step 1: Create the structure element for tagging the image
                        PdfStructElem structElem = new PdfStructElem(pdfDocument, PdfName.Figure);
                        structElem.put(PdfName.Alt, new PdfString(altText));

                        // Step 2: Create the Image element with fixed positioning and flipped height
                        Image image = new Image(imageData.getImage())
                                .setFixedPosition(pageNumber + 1, x, adjustedY)
                                .scaleAbsolute(width, -height);  // Negative height to flip vertically

                        // Step 3: Set the accessibility properties directly as strings
                        image.getAccessibilityProperties().setRole("Figure");
                        image.getAccessibilityProperties().setAlternateDescription(altText);

                        // Step 4: Add the image to the document
                        document.add(image);

                        logger.info("Reinserted and tagged image on page " + (pageNumber + 1) + " at x=" + x + ", adjusted y=" + adjustedY);
                    } else {
                        logger.warn("No image found for tag " + i + " on page " + (pageNumber + 1));
                    }
                }
            }
        }
    }



    private Map<Integer, List<ImageDataWithPosition>> extractAndStoreImages(PDDocument document, JSONArray tags) {
        Map<Integer, List<ImageDataWithPosition>> pageImagesMap = new HashMap<>();
        int pageIndex = 0;

        for (PDPage page : document.getPages()) {
            List<ImageDataWithPosition> imageList = new ArrayList<>();
            PDResources resources = page.getResources();
            float pageHeight = page.getMediaBox().getHeight(); // Get page height for coordinate adjustment

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

                        // Adjust y-coordinate by flipping it
                        Rectangle position = new Rectangle(
                                0,
                                pageHeight - pdfImageXObject.getHeight(),  // Adjust the y position to flip it
                                pdfImageXObject.getWidth(),
                                pdfImageXObject.getHeight()
                        );

                        ImageDataWithPosition imageDataWithPosition = new ImageDataWithPosition(
                                pdfImageXObject,
                                pageIndex + 1,
                                position
                        );
                        imageList.add(imageDataWithPosition);
                        logger.info("Extracted and adjusted image position for page " + (pageIndex + 1));
                    }
                } catch (IOException e) {
                    logger.error("Failed to extract image", e);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
            pageImagesMap.put(pageIndex, imageList);
            pageIndex++;
        }
        return pageImagesMap;
    }





    private byte[] convertBufferedImageToByteArray(BufferedImage image) throws IOException, java.io.IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }

    private Rectangle calculatePosition(JSONObject tag, float pageHeight) {
        float cmToPoints = 28.3465f;
        float x = (float) tag.optDouble("x", 0) * cmToPoints;
        float y = (float) tag.optDouble("y", 0) * cmToPoints;
        float width = (float) tag.optDouble("width", 100) * cmToPoints;
        float height = (float) tag.optDouble("height", 12) * cmToPoints;

        float adjustedY = pageHeight - y - height;
        return new Rectangle(x, adjustedY, width, height);
    }
    private void reinsertImagesAndTag(PdfDocument outputPdfDocument, List<ImageDataWithPosition> imageList, Document pdfDoc) {
        for (ImageDataWithPosition imgDataWithPos : imageList) {
            PdfImageXObject imageXObject = imgDataWithPos.getImage();
            Image img = new Image(imageXObject);

            img.setFixedPosition(imgDataWithPos.getRect().getX(), imgDataWithPos.getRect().getY());

            pdfDoc.add(img);
            PdfStructElem structElem = new PdfStructElem(outputPdfDocument, PdfName.Figure);
            outputPdfDocument.getStructTreeRoot().addKid(structElem);
            structElem.put(PdfName.Alt, new PdfString("Extracted Cropped Image"));

            logger.info("Reinserted and tagged an image at " + imgDataWithPos.getRect());
        }
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
                logger.info(indent + "Tag: " + elem.getRole());
                PdfString actualText = elem.getPdfObject().getAsString(PdfName.ActualText);
                if (actualText != null) {
                    logger.info(indent + "  ActualText: " + actualText.toString());
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
                        logger.info("Font found on page " + i + ": " + fontName.getValue());
                    }
                } else {
                    logger.warn("No fonts found on page " + i + ".");
                }
            }
        } catch (Exception e) {
            logger.error("Error logging fonts: " + e.getMessage());
        }
    }


    private void extractAndReinsertText(PdfDocument inputPdfDocument, PdfDocument outputPdfDocument, JSONObject element, PdfStructElem parentStructElem, Document document) throws IOException, java.io.IOException {
        float cmToPoints = 28.3465f;  // Conversion from cm to points
        int pageNumber = element.optInt("page", 1);
        float x = (float) element.optDouble("x", 0) * cmToPoints;
        float y = (float) element.optDouble("y", 0) * cmToPoints;
        float width = (float) element.optDouble("width", 100) * cmToPoints;
        float height = (float) element.optDouble("height", 12) * cmToPoints;
        String tag = element.optString("tag", "Paragraph").trim().trim().toUpperCase();  // Default tag is Paragraph

        // Base64 font data passed in the 'font' tag element; default to an empty string if not provided
        String base64Font = element.optString("font", "");

        PdfPage inputPage = inputPdfDocument.getPage(pageNumber);
        Rectangle rect = new Rectangle(x, inputPage.getPageSize().getHeight() - y - height, width, height);

        CustomTextRenderListener listener = new CustomTextRenderListener(rect);
        PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
        processor.processPageContent(inputPage);

        for (CustomTextRenderListener.TextWithPosition textLine : listener.getTextWithPositionList()) {
            Rectangle boundingBox = textLine.getBoundingBox();
            PdfFont font;

            try {
                // Handle empty font by applying a fallback if no font data is provided
                if (base64Font.isEmpty()) {
                    logger.warn("No font provided for tag '{}', using Helvetica as fallback.", tag);
                    font = PdfFontFactory.createFont(StandardFonts.HELVETICA, PdfEncodings.WINANSI);
                } else {
                    // Decode Base64 font data if available
                    byte[] fontBytes = Base64.getDecoder().decode(base64Font);
                    FontProgram fontProgram = FontProgramFactory.createFont(fontBytes);
                    font = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                }
            } catch (IOException e) {
                logger.warn("Failed to load font from Base64 data. Falling back to Helvetica.");
                font = PdfFontFactory.createFont(StandardFonts.HELVETICA, PdfEncodings.WINANSI);
            }

            // Set up the paragraph using the text’s original properties
            Paragraph p = new Paragraph(textLine.getText())
                    .setFixedPosition(pageNumber, boundingBox.getX(), boundingBox.getY(), boundingBox.getWidth())
                    .setFont(font)
                    .setFontSize(textLine.getFontSize())
                    .setFontColor(textLine.getFontColor() != null ? textLine.getFontColor() : ColorConstants.BLACK);

            document.add(p);

            // Generalized tagging structure for accessibility based on tag name
            PdfStructElem structElem = new PdfStructElem(outputPdfDocument, new PdfName(tag));
            structElem.put(PdfName.S, new PdfName(tag));  // Sets the structural tag, dynamically determined
            parentStructElem.addKid(structElem);

            logger.info("Reinserted and tagged text line '{}' with tag '{}' at exact position on page {}", textLine.getText(), tag, pageNumber);
        }
    }


    public void processAndReinsertImages(
            PdfDocument outputPdfDocument,
            JSONArray tags,
            Map<Integer, List<PDImageXObject>> pageImagesMap,
            Document document
    ) throws IOException, java.io.IOException {

        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.getJSONObject(i);

            if (tag.getString("type").equals("image")) {
                int pageNumber = tag.optInt("page", 1) - 1;
                float cmToPoints = 28.3465f;
                float x = (float) tag.optDouble("x", 0) * cmToPoints;
                float y = (float) tag.optDouble("y", 0) * cmToPoints;
                float width = (float) tag.optDouble("width", 100) * cmToPoints;
                float height = (float) tag.optDouble("height", 12) * cmToPoints;

                PdfPage outputPage = outputPdfDocument.getPage(pageNumber + 1);
                float pageHeight = outputPage.getPageSize().getHeight();
                float adjustedY = pageHeight - y - height;

                List<PDImageXObject> imagesOnPage = pageImagesMap.get(pageNumber);
                if (imagesOnPage != null && !imagesOnPage.isEmpty()) {
                    PDImageXObject pdImage = imagesOnPage.get(i % imagesOnPage.size());
                    BufferedImage bufferedImage = pdImage.getImage();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bufferedImage, "png", baos);
                    ImageData imageData = ImageDataFactory.create(baos.toByteArray());

                    Image image = new Image(imageData).setFixedPosition(x, adjustedY, width);
                    document.add(image);

                    // Tagging the image for accessibility
                    PdfStructElem structElem = new PdfStructElem(outputPdfDocument, PdfName.Figure);
                    String altText = tag.optString("alt", "Accessible Image");
                    structElem.put(PdfName.Alt, new PdfString(altText));

                    logger.info("Reinserted and tagged image at x=" + x + ", y=" + adjustedY + " on page " + (pageNumber + 1));
                } else {
                    logger.warn("No image found for tag " + i + " on page " + (pageNumber + 1));
                }
            }
        }
    }


    private ImageDataWithPosition cropImageRegion(PdfPage inputPage, Rectangle rect, int pageNumber) throws IOException, java.io.IOException {
        PdfResources resources = inputPage.getResources();

        for (PdfName xObjectName : resources.getResourceNames()) {
            PdfObject xObject = resources.getResource(xObjectName);

            if (xObject instanceof PdfStream) {
                PdfImageXObject imageXObject = new PdfImageXObject((PdfStream) xObject);
                BufferedImage bufferedImage = imageXObject.getBufferedImage();

                if (bufferedImage != null) {
                    BufferedImage croppedImage = bufferedImage.getSubimage((int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(), (int) rect.getHeight());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(croppedImage, "png", baos);
                    byte[] imageData = baos.toByteArray();

                    // Create PdfImageXObject and Image for the constructor
                    PdfImageXObject pdfImageXObject = new PdfImageXObject(ImageDataFactory.create(imageData));
                    Image image = new Image(ImageDataFactory.create(imageData)).setFixedPosition(rect.getX(), rect.getY()).scaleToFit(rect.getWidth(), rect.getHeight());

                    // Pass both PdfImageXObject and Image to the constructor
                    return new ImageDataWithPosition(pdfImageXObject, pageNumber, rect);
                }
            }
        }
        return null;
    }

    private byte[] bufferedImageToByteArray(BufferedImage image) throws IOException, java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
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