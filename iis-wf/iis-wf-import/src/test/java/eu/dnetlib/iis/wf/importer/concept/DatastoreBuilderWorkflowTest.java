package eu.dnetlib.iis.wf.importer.concept;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import eu.dnetlib.iis.common.AbstractOozieWorkflowTestCase;
import eu.dnetlib.iis.common.IntegrationTest;
import eu.dnetlib.iis.common.OozieWorkflowTestConfiguration;

/**
 * Integration test for concepts importer.
 * 
 * @author mhorst
 * 
 */
@Category(IntegrationTest.class)
public class DatastoreBuilderWorkflowTest extends AbstractOozieWorkflowTestCase {

    @Test
    public void testBuildEmptyDatastore() {
        OozieWorkflowTestConfiguration wfConf = new OozieWorkflowTestConfiguration();
        wfConf.setTimeoutInSeconds(720);
        testWorkflow("eu/dnetlib/iis/wf/importer/concept/datastore_builder/empty_input", wfConf);
    }
    
    @Test
    public void testBuildDatastore() {
        OozieWorkflowTestConfiguration wfConf = new OozieWorkflowTestConfiguration();
        wfConf.setTimeoutInSeconds(720);
        testWorkflow("eu/dnetlib/iis/wf/importer/concept/datastore_builder/test", wfConf);
    }
}
