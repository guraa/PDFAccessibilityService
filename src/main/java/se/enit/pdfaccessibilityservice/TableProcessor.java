package se.enit.pdfaccessibilityservice;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.navigation.PdfDestination;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Processes tables directly without using iText's Table structures to ensure exact positioning.
 */
public class TableProcessor {
    private static final Logger logger = LoggerFactory.getLogger(TableProcessor.class);
    private final float cmToPoints = 28.3465f; // Conversion from cm to points
    private final Set<String> processedTableIds = new HashSet<>();
    /**
     * Processes and adds a table to the PDF document by recreating it exactly like the original.
     */
    public void processTable(
            PdfDocument inputPdfDocument,
            PdfDocument outputPdfDocument,
            se.enit.pdfaccessibilityservice.TaggingInfo tableInfo,
            PdfStructElem parentStructElem,
            Document document,
            Map<String, PdfOutline> bookmarks) throws IOException {

        logger.info("Processing table with exact matching: {}", tableInfo.getName());

        if (processedTableIds.contains(tableInfo.getId())) {
            logger.warn("Table with ID {} has already been processed, skipping", tableInfo.getId());
            return;
        }
        processedTableIds.add(tableInfo.getId());

        int pageNumber = tableInfo.getPage();
        PdfPage inputPage = inputPdfDocument.getPage(pageNumber);
        PdfPage outputPage = outputPdfDocument.getPage(pageNumber);

        // Get exact coordinates
        float x = (float) tableInfo.getX() * cmToPoints;
        float y = (float) tableInfo.getY() * cmToPoints;
        float width = (float) tableInfo.getWidth() * cmToPoints;
        float height = (float) tableInfo.getHeight() * cmToPoints;

        // Get page height for coordinate conversion
        float pageHeight = inputPage.getPageSize().getHeight();

        // Create table structure element for accessibility
        PdfStructElem tableStructElem = new PdfStructElem(outputPdfDocument, new PdfName("Table"));
        parentStructElem.addKid(tableStructElem);

        // Add accessibility metadata (but not visible)
        se.enit.pdfaccessibilityservice.WcagTableData wcagData = tableInfo.getWcagData();
        if (wcagData != null && wcagData.getCaption() != null && !wcagData.getCaption().isEmpty()) {
            PdfStructElem captionStructElem = new PdfStructElem(outputPdfDocument, new PdfName("Caption"));
            tableStructElem.addKid(captionStructElem);
            captionStructElem.put(PdfName.Alt, new PdfString(wcagData.getCaption()));
        }

        // Add bookmark if needed
        String id = tableInfo.getId();
        if (id != null && !id.isEmpty() && bookmarks.containsKey(id)) {
            PdfOutline bookmark = bookmarks.get(id);
            PdfDestination destination = PdfExplicitDestination.createXYZ(
                    outputPage, x, pageHeight - y, 1);
            bookmark.addDestination(destination);
            logger.info("Linked bookmark for table ID: {} on page: {}", id, pageNumber);
        }

        // Extract all cell text with exact position and formatting
        List<se.enit.pdfaccessibilityservice.TableCellData> cellsData = extractTableCells(
                inputPdfDocument,
                tableInfo,
                pageHeight);

        // Get row and column positions
        List<Float> rowPositions = tableInfo.getRowPositions();
        List<Float> colPositions = tableInfo.getColPositions();

        // Clear the table area to ensure no original content remains
        clearTableArea(outputPage, x, y, width, height, pageHeight);

        // Create table structure elements for rows and cells
        PdfStructElem tbodyElem = new PdfStructElem(outputPdfDocument, new PdfName("TBody"));
        tableStructElem.addKid(tbodyElem);

        // Group cells by row
        Map<Integer, List<se.enit.pdfaccessibilityservice.TableCellData>> rowMap = new HashMap<>();
        for (se.enit.pdfaccessibilityservice.TableCellData cellData : cellsData) {
            rowMap.computeIfAbsent(cellData.getRow(), k -> new ArrayList<>()).add(cellData);
        }

        // Sort rows
        List<Integer> rowIndices = new ArrayList<>(rowMap.keySet());
        java.util.Collections.sort(rowIndices);

        // Process each row
        for (Integer rowIdx : rowIndices) {
            // Create row element
            PdfStructElem trElem = new PdfStructElem(outputPdfDocument, new PdfName("TR"));
            tbodyElem.addKid(trElem);

            List<se.enit.pdfaccessibilityservice.TableCellData> rowCells = rowMap.get(rowIdx);
            rowCells.sort(java.util.Comparator.comparingInt(TableCellData::getCol));

            // Process each cell
            for (se.enit.pdfaccessibilityservice.TableCellData cellData : rowCells) {
                // Determine cell type (header or data)
                String cellRole = cellData.isHeader() ? "TH" : "TD";
                PdfStructElem cellElem = new PdfStructElem(outputPdfDocument, new PdfName(cellRole));
                trElem.addKid(cellElem);

                // Get exact position of the cell
                Rectangle cellRect = cellData.getRect();

                // Position at exact coordinates from the original
                float cellX = cellRect.getX();
                float cellY = cellRect.getY();

                // Create paragraph for the cell content
                Paragraph cellParagraph = new Paragraph()
                        .setFixedPosition(cellX, cellY, cellRect.getWidth())
                        .setMargin(0)
                        .setPadding(0)
                        .setMultipliedLeading(1.0f); // Exact line heights

                // Apply the exact font properties
                if (cellData.getFontName() != null && !cellData.getFontName().isEmpty()) {
                    if (cellData.getFontSize() > 0) {
                        cellParagraph.setFontSize(cellData.getFontSize());
                    }
                    if (cellData.getFontColor() != null) {
                        cellParagraph.setFontColor(cellData.getFontColor());
                    }
                }

                // Handle any special formatting in the text
                Text cellText = new Text(cellData.getContent());
                if (cellData.isHeader()) {
                    cellText.setBold();
                }

                cellParagraph.add(cellText);

                // Set accessibility properties
                cellParagraph.getAccessibilityProperties().setRole(cellRole);

                // Add the cell content at the exact position
                document.add(cellParagraph);

                logger.info("Added cell [{}][{}] at exact position ({}, {})",
                        rowIdx, cellData.getCol(), cellX, cellY);
            }
        }

        logger.info("Table {} successfully added with exact positioning", tableInfo.getName());
    }

    /**
     * Extracts cell data with exact positioning from the original PDF.
     */
    private List<se.enit.pdfaccessibilityservice.TableCellData> extractTableCells(
            PdfDocument inputPdfDocument,
            se.enit.pdfaccessibilityservice.TaggingInfo tableInfo,
            float pageHeight) throws IOException {

        List<se.enit.pdfaccessibilityservice.TableCellData> cellsData = new ArrayList<>();
        int pageNumber = tableInfo.getPage();
        PdfPage page = inputPdfDocument.getPage(pageNumber);

        List<Float> rowPositions = tableInfo.getRowPositions();
        List<Float> colPositions = tableInfo.getColPositions();

        if (rowPositions == null || colPositions == null ||
                rowPositions.size() < 2 || colPositions.size() < 2) {
            logger.error("Invalid table positions data");
            throw new IOException("Invalid table positions data");
        }

        // Determine header rows/columns
        se.enit.pdfaccessibilityservice.WcagTableData wcagData = tableInfo.getWcagData();
        List<Integer> headerRows = wcagData != null ? wcagData.getHeaderRows() : new ArrayList<>();
        List<Integer> headerCols = wcagData != null ? wcagData.getHeaderCols() : new ArrayList<>();

        // Base coordinates
        float baseX = (float) tableInfo.getX() * cmToPoints;
        float baseY = (float) tableInfo.getY() * cmToPoints;

        // Process each cell with exact coordinates
        for (int rowIdx = 0; rowIdx < rowPositions.size() - 1; rowIdx++) {
            for (int colIdx = 0; colIdx < colPositions.size() - 1; colIdx++) {
                // Calculate cell coordinates EXACTLY matching original
                float cellX = baseX + colPositions.get(colIdx) * cmToPoints;
                float cellY = baseY + rowPositions.get(rowIdx) * cmToPoints;
                float cellWidth = (colPositions.get(colIdx + 1) - colPositions.get(colIdx)) * cmToPoints;
                float cellHeight = (rowPositions.get(rowIdx + 1) - rowPositions.get(rowIdx)) * cmToPoints;

                // Create extraction rectangle
                Rectangle extractionRegion = new Rectangle(
                        cellX,
                        pageHeight - cellY - cellHeight,
                        cellWidth,
                        cellHeight);

                // Extract text with complete formatting details
                CustomTextRenderListener listener = new CustomTextRenderListener(extractionRegion);
                PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
                processor.processPageContent(page);

                // Process text to preserve all spacing and formatting
                listener.preprocessYCoordinates();
                String cellText = listener.getCleanExtractedText();

                // Important: Do NOT trim the text to preserve exact spacing

                // Get all font details
                String fontName = listener.getExtractedFontName();
                float fontSize = listener.getExtractedFontSize();

                // Determine if header
                boolean isHeader = headerRows.contains(rowIdx) || headerCols.contains(colIdx);

                // Create cell data with exact positioning and formatting
                se.enit.pdfaccessibilityservice.TableCellData cellData = new se.enit.pdfaccessibilityservice.TableCellData(
                        rowIdx, colIdx, extractionRegion, cellText, isHeader,
                        fontName, fontSize, listener.getExtractedFontColor());
                cellsData.add(cellData);

                logger.info("Extracted table cell [{}][{}] at EXACT position ({}, {}): Text='{}', Font='{}', Size={}",
                        rowIdx, colIdx, cellX, cellY, cellText, fontName, fontSize);
            }
        }

        return cellsData;
    }

    /**
     * Clears the table area.
     */
    public void clearTableArea(PdfPage page, float x, float y, float width, float height, float pageHeight) {
        logger.info("Clearing table area: x={}, y={}, width={}, height={}", x, pageHeight - y - height, width, height);
        PdfCanvas canvas = new PdfCanvas(page);
        canvas.saveState();
        canvas.beginMarkedContent(PdfName.Artifact);
        canvas.setFillColor(ColorConstants.WHITE);
        canvas.rectangle(x, pageHeight - y - height, width, height);
        canvas.fill();
        canvas.endMarkedContent();
        canvas.restoreState();
    }
}