package eu.dnetlib.iis.common.pig.udfs;

import java.io.IOException;

import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.junit.Test;

import com.google.common.collect.Lists;

import junit.framework.TestCase;

/**
 *
 * @author Dominika Tkaczyk
 */
public class StringBagsMergerTest extends TestCase {
    
    @Test
	public void testUDF() throws IOException {
        // given
        StringBagsMerger udf = new StringBagsMerger();
        TupleFactory tupleFactory = TupleFactory.getInstance();
        BagFactory bagFactory = BagFactory.getInstance();
        DataBag emptyBag = bagFactory.newDefaultBag();
        DataBag bag1 = bagFactory.newDefaultBag(Lists.newArrayList(tupleFactory.newTuple("tup1"), 
                                                                   tupleFactory.newTuple("tup2")));
        DataBag bag2 = bagFactory.newDefaultBag(Lists.newArrayList(tupleFactory.newTuple("tup3"), 
                                                                   tupleFactory.newTuple("tup4")));
        DataBag bag3 = bagFactory.newDefaultBag(Lists.newArrayList(tupleFactory.newTuple("tup1"), 
                                                                   tupleFactory.newTuple("tup4"),
                                                                   tupleFactory.newTuple("tup5")));
        DataBag bag4 = bagFactory.newDefaultBag(Lists.newArrayList(tupleFactory.newTuple("tup1"), 
                                                                   tupleFactory.newTuple("tup2"),
                                                                   tupleFactory.newTuple("tup3"),
                                                                   tupleFactory.newTuple("tup4"),
                                                                   tupleFactory.newTuple("tup5")));
        // execute & assert
        assertNull(udf.exec(null));
        assertNull(udf.exec(tupleFactory.newTuple()));
        assertNull(udf.exec(tupleFactory.newTuple((DataBag)null)));
        assertNull(udf.exec(tupleFactory.newTuple(emptyBag)));
        assertEquals(bag1, udf.exec(tupleFactory.newTuple(bag1)));
        assertEquals(bag4, udf.exec(tupleFactory.newTuple(Lists.newArrayList(bag1, emptyBag, bag2, bag3))));
    }
    
    @Test
    public void testOutputSchema() throws Exception {
        // given
        StringBagsMerger udf = new StringBagsMerger();
        Schema irrelevantSchema = null;
        
        // execute
        Schema resultSchema = udf.outputSchema(irrelevantSchema);
        
        // assert
        assertNotNull(resultSchema);
        assertEquals(1, resultSchema.getFields().size());
        assertEquals(DataType.BAG, resultSchema.getField(0).type);
        assertEquals(1, resultSchema.getField(0).schema.getFields().size());
        assertEquals(DataType.TUPLE, resultSchema.getField(0).schema.getField(0).type);
        assertEquals(1, resultSchema.getField(0).schema.getField(0).schema.getFields().size());
        assertEquals(DataType.CHARARRAY, resultSchema.getField(0).schema.getField(0).schema.getField(0).type);
    }
    
}
