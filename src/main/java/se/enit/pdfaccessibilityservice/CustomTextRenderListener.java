package se.enit.pdfaccessibilityservice;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import java.util.ArrayList;
import java.util.List;

public class CustomTextRenderListener extends LocationTextExtractionStrategy {

    private String extractedFontName;
    private float extractedFontSize;
    private Color extractedFontColor;
    private StringBuilder extractedText = new StringBuilder();
    private Rectangle filterRectangle;

    // List to store each line's text along with its exact position
    private final List<TextWithPosition> textWithPositionList = new ArrayList<>();

    // Constructor accepts a rectangle filter
    public CustomTextRenderListener(Rectangle filterRectangle) {
        this.filterRectangle = filterRectangle;
    }

    @Override
    public void eventOccurred(IEventData data, EventType type) {
        if (type == EventType.RENDER_TEXT) {
            TextRenderInfo renderInfo = (TextRenderInfo) data;

            // Get the bounding box of the text
            Rectangle textRect = renderInfo.getDescentLine().getBoundingRectangle();

            // Check if the text is within the filter rectangle
            if (filterRectangle != null && textRect != null && rectanglesIntersect(filterRectangle, textRect)) {
                // Store the text and its properties if it's within the region
                extractedFontName = renderInfo.getFont().getFontProgram().getFontNames().getFontName();
                extractedFontSize = renderInfo.getFontSize();
                extractedFontColor = renderInfo.getFillColor();
                String text = renderInfo.getText();
                extractedText.append(text);

                // Add the text and its exact bounding box coordinates to the list
                textWithPositionList.add(new TextWithPosition(text, textRect, extractedFontName, extractedFontSize, extractedFontColor));
            }
        }

        // Call the parent method for normal text extraction behavior
        super.eventOccurred(data, type);
    }

    // Method to check if two rectangles intersect
    private boolean rectanglesIntersect(Rectangle r1, Rectangle r2) {
        return r1.getX() < r2.getX() + r2.getWidth() &&
                r1.getX() + r1.getWidth() > r2.getX() &&
                r1.getY() < r2.getY() + r2.getHeight() &&
                r1.getY() + r1.getHeight() > r2.getY();
    }

    // Accessors
    public String getExtractedFontName() {
        return extractedFontName;
    }

    public float getExtractedFontSize() {
        return extractedFontSize;
    }

    public Color getExtractedFontColor() {
        return extractedFontColor;
    }

    public String getExtractedText() {
        return extractedText.toString();
    }

    public List<TextWithPosition> getTextWithPositionList() {
        return textWithPositionList;
    }

    // Inner class to hold text with position information
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
