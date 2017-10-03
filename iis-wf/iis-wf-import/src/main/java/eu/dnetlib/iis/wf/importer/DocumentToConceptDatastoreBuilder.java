package eu.dnetlib.iis.wf.importer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;

import eu.dnetlib.iis.common.WorkflowRuntimeParameters;
import eu.dnetlib.iis.common.java.PortBindings;
import eu.dnetlib.iis.common.java.Process;
import eu.dnetlib.iis.common.java.io.DataStore;
import eu.dnetlib.iis.common.java.io.FileSystemPath;
import eu.dnetlib.iis.common.java.porttype.AvroPortType;
import eu.dnetlib.iis.common.java.porttype.PortType;
import eu.dnetlib.iis.referenceextraction.researchinitiative.schemas.DocumentToConceptId;

/**
 * Process building {@link DocumentToConceptId} based on datastore records specification provided as input parameter.
 * 
 * The following csv syntax is allowed: 
 * 
 * docId1,conceptId1;docId2,conceptId2,conceptId3
 * 
 * where two kinds of delimiters are used:
 * ',' separating publication identifier from one or more virtual organizations (also used for separating VOs when more than one specified)
 * ';' separating sets of blacklisting specifications for publication
 * 
 * @author mhorst
 *
 */
public class DocumentToConceptDatastoreBuilder implements Process {

    /**
     * Parameter name holding CSV of document to concept relations to be written.
     */
    protected static final String PARAM_NAME_CSV = "document.to.concept.csv";

    protected static final String PORT_OUT = "output";

    protected static final char specGroupDelimiter = ';';
    
    protected static final char idsDelimiter = ',';
    
    private final Logger log = Logger.getLogger(this.getClass());

    private final Map<String, PortType> outputPorts = new HashMap<String, PortType>();
    
    private static final float defaultConvidenceLevel = 1;
    
    
    // ------------------------ CONSTRUCTORS -------------------
    
    public DocumentToConceptDatastoreBuilder() {
        this.outputPorts.put(PORT_OUT, new AvroPortType(DocumentToConceptId.SCHEMA$));
    }
    
    
    // ------------------------ LOGIC --------------------------

    @Override
    public Map<String, PortType> getInputPorts() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, PortType> getOutputPorts() {
        return outputPorts;
    }

    @Override
    public void run(PortBindings portBindings, Configuration conf, Map<String, String> parameters) throws Exception {
        
        Preconditions.checkArgument(parameters.containsKey(PARAM_NAME_CSV),
                "unspecified identifiers, required parameter '%s' is missing!", PARAM_NAME_CSV);

        FileSystemPath identifierOutput = new FileSystemPath(FileSystem.get(conf), portBindings.getOutput().get(PORT_OUT));

        try (DataFileWriter<DocumentToConceptId> mdStoreIdentifierWriter = createWriter(identifierOutput, DocumentToConceptId.SCHEMA$)) {
            String docToConceptCSV = parameters.get(PARAM_NAME_CSV);

            if (StringUtils.isNotBlank(docToConceptCSV) && !WorkflowRuntimeParameters.UNDEFINED_NONEMPTY_VALUE.equals(docToConceptCSV)) {

                String[] idGroups = StringUtils.split(docToConceptCSV.trim(), specGroupDelimiter);

                for (String currentIdGroup : idGroups) {
                    if (StringUtils.isNotBlank(currentIdGroup)) {
                        String[] ids = StringUtils.split(currentIdGroup.trim(), idsDelimiter);
                        if (ids.length > 1 && StringUtils.isNotBlank(ids[0])) {
                            String documentId = ids[0].trim();
                            for (int i = 1; i < ids.length; i++) {
                                String conceptId = ids[i].trim();
                                if (StringUtils.isNotBlank(conceptId)) {
                                    DocumentToConceptId.Builder documentToConceptBuilder = DocumentToConceptId.newBuilder();
                                    documentToConceptBuilder.setDocumentId(documentId);
                                    documentToConceptBuilder.setConceptId(conceptId);
                                    documentToConceptBuilder.setConfidenceLevel(defaultConvidenceLevel);
                                    mdStoreIdentifierWriter.append(documentToConceptBuilder.build());                                    
                                }
                            }

                        } else {
                            log.warn("invalid identifiers group specification: " + currentIdGroup);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Provides avro writer.
     * @throws IOException 
     */
    protected DataFileWriter<DocumentToConceptId> createWriter(FileSystemPath path, Schema schema) throws IOException {
        return DataStore.create(path, schema);
    }

}
