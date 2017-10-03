package eu.dnetlib.iis.wf.importer;

import static eu.dnetlib.iis.wf.importer.DocumentToConceptDatastoreBuilder.PARAM_NAME_CSV;
import static eu.dnetlib.iis.wf.importer.DocumentToConceptDatastoreBuilder.PORT_OUT;
import static eu.dnetlib.iis.wf.importer.DocumentToConceptDatastoreBuilder.specGroupDelimiter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import eu.dnetlib.iis.common.java.PortBindings;
import eu.dnetlib.iis.common.java.io.FileSystemPath;
import eu.dnetlib.iis.common.java.porttype.AvroPortType;
import eu.dnetlib.iis.common.java.porttype.PortType;
import eu.dnetlib.iis.referenceextraction.researchinitiative.schemas.DocumentToConceptId;


/**
 * @author mhorst
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class DocumentToConceptDatastoreBuilderTest {
    
    private PortBindings portBindings;
    
    private Configuration conf;
    
    private Map<String, String> parameters;
    
    private DocumentToConceptDatastoreBuilder identifierDatastoreBuilder;
    
    @Mock
    private DataFileWriter<DocumentToConceptId> identifierWriter;
    
    @Captor
    private ArgumentCaptor<DocumentToConceptId> identifierCaptor;
    
    @Before
    public void init() throws Exception {

        
        Map<String, Path> output = new HashMap<>();
        output.put(PORT_OUT, new Path("/irrelevant/location/as/it/will/be/mocked"));
        this.portBindings = new PortBindings(Collections.emptyMap(), output);
        this.conf = new Configuration();
        this.parameters = new HashMap<>();
        
        this.identifierDatastoreBuilder = new DocumentToConceptDatastoreBuilder() {
          
            @Override
            protected DataFileWriter<DocumentToConceptId> createWriter(FileSystemPath path, Schema schema)
                    throws IOException {
                return identifierWriter;
            }
        };
    }
    
    // ----------------------------------- TESTS -----------------------------------
    
    @Test
    public void testGetInputPorts() throws Exception {
        // execute
        Map<String, PortType> result = identifierDatastoreBuilder.getInputPorts();
        
        // assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testGetOutputPorts() throws Exception {
        // execute
        Map<String, PortType> result = identifierDatastoreBuilder.getOutputPorts();
        
        // assert
        assertNotNull(result);
        assertNotNull(result.get(PORT_OUT));
        assertTrue(result.get(PORT_OUT) instanceof AvroPortType);
        assertTrue(DocumentToConceptId.SCHEMA$ == ((AvroPortType)result.get(PORT_OUT)).getSchema());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRunWithoutIdentifierParam() throws Exception {
        // execute
        identifierDatastoreBuilder.run(portBindings, conf, parameters);
    }
    
    @Test
    public void testRunWithBlankInputParam() throws Exception {
        // given
        parameters.put(PARAM_NAME_CSV, "");
        
        // execute
        identifierDatastoreBuilder.run(portBindings, conf, parameters);
        
        // assert
        verify(identifierWriter, never()).append(any());
    }
    
    @Test
    public void testRunWithInvalidInputParam() throws Exception {
        // given
        parameters.put(PARAM_NAME_CSV, "invalid");
        
        // execute
        identifierDatastoreBuilder.run(portBindings, conf, parameters);
        
        // assert
        verify(identifierWriter, never()).append(any());
    }
    
    @Test
    public void testRun() throws Exception {
        // given
        String chunk1 = "docId1,conceptId1";
        String chunk2 = "docId2,conceptId2,conceptId3";
        parameters.put(PARAM_NAME_CSV, buildIdentifiers(chunk1, chunk2));
        
        // execute
        identifierDatastoreBuilder.run(portBindings, conf, parameters);
        
        // assert
        verify(identifierWriter, times(3)).append(identifierCaptor.capture());
        assertEquals("docId1", identifierCaptor.getAllValues().get(0).getDocumentId());
        assertEquals("conceptId1", identifierCaptor.getAllValues().get(0).getConceptId());
        assertEquals(1, identifierCaptor.getAllValues().get(0).getConfidenceLevel(), 0.001);
        
        assertEquals("docId2", identifierCaptor.getAllValues().get(1).getDocumentId());
        assertEquals("conceptId2", identifierCaptor.getAllValues().get(1).getConceptId());
        assertEquals(1, identifierCaptor.getAllValues().get(1).getConfidenceLevel(), 0.001);
        
        assertEquals("docId2", identifierCaptor.getAllValues().get(2).getDocumentId());
        assertEquals("conceptId3", identifierCaptor.getAllValues().get(2).getConceptId());
        assertEquals(1, identifierCaptor.getAllValues().get(2).getConfidenceLevel(), 0.001);
    }
    
    // ------------------------------- PRIVATE -----------------------------------
    
    private static String buildIdentifiers(String... idsChunks) {
        StringBuilder strBuilder = new StringBuilder();
        for (int i=0; i < idsChunks.length; i++) {
            strBuilder.append(idsChunks[i]);
            if (i < idsChunks.length -1) {
                strBuilder.append(specGroupDelimiter);
            }
        }
        return strBuilder.toString();
    }

}
