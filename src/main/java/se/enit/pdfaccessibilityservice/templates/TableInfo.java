package se.enit.pdfaccessibilityservice.templates;

public class TableInfo {
    private int rows;
    private int columns;

    // Add other fields as needed

    // Getters and setters
    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    @Override
    public String toString() {
        return "TableInfo{" +
                "rows=" + rows +
                ", columns=" + columns +
                '}';
    }
}
