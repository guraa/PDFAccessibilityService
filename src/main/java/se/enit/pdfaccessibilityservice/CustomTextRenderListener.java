package se.enit.pdfaccessibilityservice;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CustomTextRenderListener extends LocationTextExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CustomTextRenderListener.class);

    private String extractedFontName;
    private float extractedFontSize = 10;
    private Color extractedFontColor;

    private StringBuilder extractedText = new StringBuilder();
    private StringBuilder canonicalText = new StringBuilder();

    private Rectangle filterRectangle;
    private final List<TextWithPosition> textWithPositionList = new ArrayList<>();

    private float lastY = -1;
    private float lastFontSize = 10;

    public CustomTextRenderListener(Rectangle filterRectangle) {
        this.filterRectangle = filterRectangle;
    }

    @Override
    public void eventOccurred(IEventData data, EventType type) {
        if (type == EventType.RENDER_TEXT) {
            TextRenderInfo renderInfo = (TextRenderInfo) data;
            Rectangle textRect = renderInfo.getDescentLine().getBoundingRectangle();

            if (filterRectangle != null && textRect != null && rectanglesIntersect(filterRectangle, textRect)) {
                String text = renderInfo.getText();
                float currentY = textRect.getY();
                float currentFontSize = renderInfo.getFontSize();
                float yDiff = (lastY != -1) ? Math.abs(lastY - currentY) : 0;

                // Save the text and coordinates
                canonicalText.append(String.format("[%s | Y=%.2f | FontSize=%.2f] ", text, currentY, currentFontSize));
                extractedText.append(text);

                lastY = currentY;
                lastFontSize = currentFontSize;

                extractedFontName = renderInfo.getFont().getFontProgram().getFontNames().getFontName();
                extractedFontSize = currentFontSize;
                extractedFontColor = renderInfo.getFillColor();

                textWithPositionList.add(new TextWithPosition(text, textRect, extractedFontName, extractedFontSize, extractedFontColor));
            }
        }
        super.eventOccurred(data, type);
    }

    // New method to preprocess Y-coordinates and adjust spacing
    public void preprocessYCoordinates() {
        if (textWithPositionList.isEmpty()) {
            return;
        }

        // Sort text by Y-coordinate (descending, since PDF coordinates are inverted)
        Collections.sort(textWithPositionList, Comparator.comparingDouble((TextWithPosition t) -> t.getBoundingBox().getY()).reversed());

        StringBuilder adjustedText = new StringBuilder();
        float previousY = -1;

        for (TextWithPosition textPosition : textWithPositionList) {
            float currentY = textPosition.getBoundingBox().getY();
            float fontSize = textPosition.getFontSize();
            float lineSpacingThreshold = Math.max(fontSize * 1.0f, 7);
            float paragraphSpacingThreshold = Math.max(fontSize * 3.0f, 12);

            // Calculate Y difference for line/paragraph breaks
            if (previousY != -1) {
                float yDiff = Math.abs(previousY - currentY);

                logger.info("yDiff: " + yDiff + " lineSpacingThreshold: " + lineSpacingThreshold + " paragraphSpacingThreshold: " + paragraphSpacingThreshold);

                if (yDiff > paragraphSpacingThreshold) {
                    adjustedText.append("\n\n");  // Paragraph break
                } else if (yDiff > lineSpacingThreshold) {
                    adjustedText.append("\n");  // Single line break
                }
            }

            adjustedText.append(textPosition.getText());
            previousY = currentY;
        }

        // Replace the extracted text with the adjusted one
        extractedText = adjustedText;
    }

    // Return clean text without Y-coordinates (for reinsertion)
    public String getCleanExtractedText() {
        return extractedText.toString();
    }

    // Log canonical format (for debugging purposes)
    public String getCanonicalExtractedText() {
        return canonicalText.toString();
    }

    // Check for rectangle intersection
    private boolean rectanglesIntersect(Rectangle r1, Rectangle r2) {
        return r1.getX() < r2.getX() + r2.getWidth() &&
                r1.getX() + r1.getWidth() > r2.getX() &&
                r1.getY() < r2.getY() + r2.getHeight() &&
                r1.getY() + r1.getHeight() > r2.getY();
    }

    public String getExtractedFontName() {
        return extractedFontName;
    }

    public float getExtractedFontSize() {
        return extractedFontSize;
    }

    public Color getExtractedFontColor() {
        return extractedFontColor;
    }

    public List<TextWithPosition> getTextWithPositionList() {
        return textWithPositionList;
    }

    public static class TextWithPosition {
        private final String text;
        private final Rectangle boundingBox;
        private final String fontName;
        private final float fontSize;
        private final Color fontColor;

        public TextWithPosition(String text, Rectangle boundingBox, String fontName, float fontSize, Color fontColor) {
            this.text = text;
            this.boundingBox = boundingBox;
            this.fontName = fontName;
            this.fontSize = fontSize;
            this.fontColor = fontColor;
        }

        public String getText() {
            return text;
        }

        public Rectangle getBoundingBox() {
            return boundingBox;
        }

        public String getFontName() {
            return fontName;
        }

        public float getFontSize() {
            return fontSize;
        }

        public Color getFontColor() {
            return fontColor;
        }
    }
}
