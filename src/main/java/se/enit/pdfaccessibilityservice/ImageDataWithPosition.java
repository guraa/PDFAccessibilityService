package se.enit.pdfaccessibilityservice;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;

public class ImageDataWithPosition {
    private final PdfImageXObject image;
    private final int page;
    private final Rectangle rect;

    private final String id;

    // Updated constructor
    public ImageDataWithPosition(PdfImageXObject image, int page, Rectangle rect, String id) {
        this.image = image;
        this.page = page;
        this.rect = rect;
        this.id = id;

    }

    // Getter for image
    public PdfImageXObject getImage() {
        return image;
    }

    // Getter for page
    public int getPage() {
        return page;
    }

    // Getter for rect
    public Rectangle getRect() {
        return rect;
    }

    public String getId() {
        return id;
    }

}
