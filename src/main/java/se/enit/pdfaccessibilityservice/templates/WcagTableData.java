package se.enit.pdfaccessibilityservice;

import java.util.List;

public class WcagTableData {
    private boolean hasHeader;
    private List<Integer> headerRows;
    private List<Integer> headerCols;
    private String summary;
    private String caption;
    private String scope;
    private boolean isComplex;

    // Getters and Setters
    public boolean isHasHeader() {
        return hasHeader;
    }

    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    public List<Integer> getHeaderRows() {
        return headerRows;
    }

    public void setHeaderRows(List<Integer> headerRows) {
        this.headerRows = headerRows;
    }

    public List<Integer> getHeaderCols() {
        return headerCols;
    }

    public void setHeaderCols(List<Integer> headerCols) {
        this.headerCols = headerCols;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isComplex() {
        return isComplex;
    }

    public void setComplex(boolean complex) {
        isComplex = complex;
    }

    @Override
    public String toString() {
        return "WcagTableData{" +
                "hasHeader=" + hasHeader +
                ", headerRows=" + headerRows +
                ", headerCols=" + headerCols +
                ", summary='" + summary + '\'' +
                ", caption='" + caption + '\'' +
                ", scope='" + scope + '\'' +
                ", isComplex=" + isComplex +
                '}';
    }
}