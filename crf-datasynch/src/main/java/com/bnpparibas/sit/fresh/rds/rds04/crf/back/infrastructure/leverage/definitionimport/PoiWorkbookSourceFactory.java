package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .UnreadableWorkbookException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.WorkbookSource;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .WorkbookSourceFactory;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.io.InputStream;

/**
 * ADAPTER. Together with {@code PoiWorkbookSource}, the only place Apache POI is named.
 *
 * <p>Translates POI's failures into {@link UnreadableWorkbookException} so nothing above this
 * layer has to know what {@code NotOfficeXmlFileException} is — that is precisely the leak this
 * factory exists to stop.
 */
@DomainDrivenDesign.InfrastructureService
public final class PoiWorkbookSourceFactory implements WorkbookSourceFactory {

    @Override
    public WorkbookSource open(InputStream stream) {
        try {
            return new PoiWorkbookSource(stream);
        } catch (Exception ex) {
            // Deliberately broad: POI signals a bad file with several unrelated types
            // (NotOfficeXmlFileException, EncryptedDocumentException, plain IOException, and a
            // POIXMLException for a truncated zip). Naming them here would put POI back in the
            // signature of every caller.
            throw new UnreadableWorkbookException(
                    "The uploaded file could not be read as an .xlsx workbook", ex);
        }
    }
}
