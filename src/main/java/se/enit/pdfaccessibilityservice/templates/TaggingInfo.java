package se.enit.pdfaccessibilityservice;

import java.util.List;

public class TaggingInfo {

    private String type;
    private String name;
    private String tag;
    private String language;
    private String font;
    private double x;
    private double y;
    private double width;
    private double height;
    private int page;
    private String alt;
    private boolean isArtifact;
    private boolean containsTable;
    private String id;
    private String section;

    // Table specific fields
    private int rowCount;
    private int colCount;
    private List<Float> rowPositions;
    private List<Float> colPositions;
    private Integer headerRow;
    private Integer headerCol;
    private se.enit.pdfaccessibilityservice.WcagTableData wcagData;

    // Getters and setters for existing fields
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public String getAlt() {
        return alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public boolean isArtifact() {
        return isArtifact;
    }

    public void setArtifact(boolean artifact) {
        isArtifact = artifact;
    }

    public boolean isContainsTable() {
        return containsTable;
    }

    public void setContainsTable(boolean containsTable) {
        this.containsTable = containsTable;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    // Getters and setters for table fields
    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getColCount() {
        return colCount;
    }

    public void setColCount(int colCount) {
        this.colCount = colCount;
    }

    public List<Float> getRowPositions() {
        return rowPositions;
    }

    public void setRowPositions(List<Float> rowPositions) {
        this.rowPositions = rowPositions;
    }

    public List<Float> getColPositions() {
        return colPositions;
    }

    public void setColPositions(List<Float> colPositions) {
        this.colPositions = colPositions;
    }

    public Integer getHeaderRow() {
        return headerRow;
    }

    public void setHeaderRow(Integer headerRow) {
        this.headerRow = headerRow;
    }

    public Integer getHeaderCol() {
        return headerCol;
    }

    public void setHeaderCol(Integer headerCol) {
        this.headerCol = headerCol;
    }

    public se.enit.pdfaccessibilityservice.WcagTableData getWcagData() {
        return wcagData;
    }

    public void setWcagData(se.enit.pdfaccessibilityservice.WcagTableData wcagData) {
        this.wcagData = wcagData;
    }

    @Override
    public String toString() {
        return "TaggingInfo{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                ", page=" + page +
                ", alt='" + alt + '\'' +
                ", isArtifact=" + isArtifact +
                ", containsTable=" + containsTable +
                ", id='" + id + '\'' +
                (containsTable ?
                        ", rowCount=" + rowCount +
                                ", colCount=" + colCount +
                                ", rowPositions=" + rowPositions +
                                ", colPositions=" + colPositions +
                                ", wcagData=" + wcagData : "") +
                '}';
    }
}