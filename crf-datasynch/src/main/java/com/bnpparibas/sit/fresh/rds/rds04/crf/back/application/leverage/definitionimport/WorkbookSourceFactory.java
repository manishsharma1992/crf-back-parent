package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.io.InputStream;

/**
 * PORT. Opens an uploaded stream as a {@link WorkbookSource}.
 *
 * <p>Exists so that the web layer never imports Apache POI. Without it the controller would have
 * to construct the adapter itself, and the one dependency the whole import is built to contain
 * would leak straight to the edge.
 */
public interface WorkbookSourceFactory {

    /**
     * @throws UnreadableWorkbookException when the stream is not a readable workbook
     */
    WorkbookSource open(InputStream stream);
}
