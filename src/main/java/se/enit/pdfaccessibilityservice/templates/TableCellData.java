package se.enit.pdfaccessibilityservice;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.geom.Rectangle;

public class TableCellData {
    private final int row;
    private final int col;
    private final Rectangle rect;
    private final String content;
    private final boolean isHeader;
    private final String fontName;
    private final float fontSize;
    private final Color fontColor;

    public TableCellData(int row, int col, Rectangle rect, String content, boolean isHeader) {
        this(row, col, rect, content, isHeader, null, 0, null);
    }

    public TableCellData(int row, int col, Rectangle rect, String content, boolean isHeader,
                         String fontName, float fontSize, Color fontColor) {
        this.row = row;
        this.col = col;
        this.rect = rect;
        this.content = content;
        this.isHeader = isHeader;
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.fontColor = fontColor;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public Rectangle getRect() {
        return rect;
    }

    public String getContent() {
        return content;
    }

    public boolean isHeader() {
        return isHeader;
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

    @Override
    public String toString() {
        return "TableCellData{" +
                "row=" + row +
                ", col=" + col +
                ", rect=" + rect +
                ", content='" + content + '\'' +
                ", isHeader=" + isHeader +
                ", fontName='" + fontName + '\'' +
                ", fontSize=" + fontSize +
                '}';
    }
}