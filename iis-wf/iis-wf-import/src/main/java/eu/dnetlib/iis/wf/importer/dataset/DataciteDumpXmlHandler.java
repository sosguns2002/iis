package eu.dnetlib.iis.wf.importer.dataset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Maps;

import eu.dnetlib.iis.common.hbase.HBaseConstants;
import eu.dnetlib.iis.importer.schemas.DataSetReference;
import eu.dnetlib.iis.importer.schemas.DatasetToMDStore;
import eu.dnetlib.iis.wf.importer.RecordReceiver;

/**
 * Datacite XML dump SAX handler.
 * Notice: writer is not being closed by handler.
 * Created outside, let it be closed outside as well.
 * @author mhorst
 *
 */
public class DataciteDumpXmlHandler extends DefaultHandler {

	private static final String ELEM_HEADER = "header";
	private static final String ELEM_PAYLOAD = "payload";
	private static final String ELEM_METADATA = "metadata";
	
	private static final String ELEM_RESOURCE = "resource";

	public static final String ELEM_IDENTIFIER = "identifier";
	public static final String ELEM_OBJ_IDENTIFIER = "objIdentifier";
	
	private static final String ELEM_CREATOR = "creator";
	private static final String ELEM_CREATOR_NAME = "creatorName";
	
	private static final String ELEM_TITLES = "titles";
	private static final String ELEM_TITLE = "title";
	
	private static final String ELEM_DESCRIPTION = "description";
	private static final String ELEM_PUBLISHER = "publisher";
	private static final String ELEM_PUBLICATION_YEAR = "publicationYear";
	private static final String ELEM_FORMATS = "formats";
	private static final String ELEM_FORMAT = "format";
	private static final String ELEM_RESOURCE_TYPE = "resourceType";
	
	private static final String ELEM_ALTERNATE_IDENTIFIERS = "alternateIdentifiers";
    private static final String ELEM_ALTERNATE_IDENTIFIER = "alternateIdentifier";
	
	private static final String ATTRIBUTE_ID_TYPE = "identifierType";
	private static final String ATTRIBUTE_RESOURCE_TYPE_GENERAL = "resourceTypeGeneral";
	private static final String ATTRIBUTE_ALTERNATE_IDENTIFIER_TYPE = "alternateIdentifierType";
	
//	lowercased identifier types
	public static final String ID_TYPE_DOI = "doi";
	
	private final Logger log = Logger.getLogger(this.getClass());
	
	private Stack<String> parents;
	
	private StringBuilder currentValue = new StringBuilder();
	
	private String headerId = null;
	
	private String idType = null;
	private String idValue = null;
	private List<CharSequence> creatorNames = null;
	private List<CharSequence> titles = null;
	private List<CharSequence> formats = null;
	private Map<CharSequence, CharSequence> alternateIdentifiers = null;
	private String publisher = null;
	private String description = null;
	private String publicationYear = null;
	private String resourceTypeClass = null;
	private String resourceTypeValue = null;
	private String currentAlternateIdentifierType = null;
	
	
	private final RecordReceiver<DataSetReference> datasetReceiver;
	
	private final RecordReceiver<DatasetToMDStore> datasetToMDStoreReceived;
	
	private final String mainIdFieldName;
	
	private final String mdStoreId;
	
	/**
	 * Default constructor.
	 * @param datasetReceiver
	 * @param datasetToMDStoreReceived
	 * @param mainIdFieldName field name to be used as main identifier. Introduced because of differences between MDStore records and XML dump records.
	 * @param mdStoreId
	 */
	public DataciteDumpXmlHandler(RecordReceiver<DataSetReference> datasetReceiver,
			RecordReceiver<DatasetToMDStore> datasetToMDStoreReceived,
			String mainIdFieldName, String mdStoreId) {
		super();
		this.datasetReceiver = datasetReceiver;
		this.datasetToMDStoreReceived = datasetToMDStoreReceived;
		this.mainIdFieldName = mainIdFieldName;
		this.mdStoreId = mdStoreId;
	}
	
	/**
	 * Default constructor.
	 * @param datasetReceiver
	 * @param datasetToMDStoreReceived
	 * @param mainIdFieldName field name to be used as main identifier. Introduced because of differences between MDStore records and XML dump records.
	 * @param mdStoreId
	 */
	public DataciteDumpXmlHandler(RecordReceiver<DataSetReference> datasetReceiver,
			RecordReceiver<DatasetToMDStore> datasetToMDStoreReceived,
			String mdStoreId) {
		this(datasetReceiver, datasetToMDStoreReceived, ELEM_OBJ_IDENTIFIER, mdStoreId);
	}
	
	@Override
	public void startDocument() throws SAXException {
		parents = new Stack<String>();
		clearAllFields();
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		if (isWithinElement(localName, mainIdFieldName, ELEM_HEADER)) {
//			identifierType attribute is mandatory
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_IDENTIFIER, ELEM_RESOURCE)) {
//			identifierType attribute is mandatory
			this.idType = attributes.getValue(ATTRIBUTE_ID_TYPE).toLowerCase();	
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_CREATOR_NAME, ELEM_CREATOR)) {
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_TITLE, ELEM_TITLES)) {
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_FORMAT, ELEM_FORMATS)) {
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_DESCRIPTION, ELEM_RESOURCE)) {
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_PUBLISHER, ELEM_RESOURCE)) {
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_PUBLICATION_YEAR, ELEM_RESOURCE)) {
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_RESOURCE_TYPE, ELEM_RESOURCE)) {
			this.resourceTypeClass = attributes.getValue(ATTRIBUTE_RESOURCE_TYPE_GENERAL);
			this.currentValue = new StringBuilder();
		} else if (isWithinElement(localName, ELEM_ALTERNATE_IDENTIFIER, ELEM_ALTERNATE_IDENTIFIERS)) {
            this.currentAlternateIdentifierType = attributes.getValue(ATTRIBUTE_ALTERNATE_IDENTIFIER_TYPE);
            this.currentValue = new StringBuilder();
        } 
		this.parents.push(localName);
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		this.parents.pop();
		if (isWithinElement(localName, mainIdFieldName, ELEM_HEADER)) {
			this.headerId = this.currentValue.toString().trim();
		} else if (isWithinElement(localName, ELEM_IDENTIFIER, ELEM_RESOURCE)) {
			this.idValue = this.currentValue.toString().trim();
		} else if (isWithinElement(localName, ELEM_CREATOR_NAME, ELEM_CREATOR) 
				&& this.currentValue.length()>0) {
			if (this.creatorNames==null) {
				this.creatorNames = new ArrayList<CharSequence>();
			}
			this.creatorNames.add(this.currentValue.toString().trim());
		} else if (isWithinElement(localName, ELEM_TITLE, ELEM_TITLES)
				&& this.currentValue.length()>0) {
			if (this.titles==null) {
				this.titles = new ArrayList<CharSequence>();
			}
			this.titles.add(this.currentValue.toString().trim());
		} else if (isWithinElement(localName, ELEM_FORMAT, ELEM_FORMATS)
				&& this.currentValue.length()>0) {
			if (this.formats==null) {
				this.formats = new ArrayList<CharSequence>();
			}
			this.formats.add(this.currentValue.toString().trim());
		} else if (isWithinElement(localName, ELEM_ALTERNATE_IDENTIFIER, ELEM_ALTERNATE_IDENTIFIERS)
                && this.currentValue.length()>0) {
            if (this.currentAlternateIdentifierType!=null) {
                if (this.alternateIdentifiers==null) {
                    this.alternateIdentifiers = Maps.newHashMap();
                }
                this.alternateIdentifiers.put(this.currentAlternateIdentifierType, this.currentValue.toString().trim());    
            }
        } else if (isWithinElement(localName, ELEM_DESCRIPTION, ELEM_RESOURCE)
				&& this.currentValue.length()>0) {
			this.description = this.currentValue.toString().trim();
		} else if (isWithinElement(localName, ELEM_PUBLISHER, ELEM_RESOURCE)
				&& this.currentValue.length()>0) {
			this.publisher = this.currentValue.toString().trim();
		} else if (isWithinElement(localName, ELEM_PUBLICATION_YEAR, ELEM_RESOURCE)
				&& this.currentValue.length()>0) {
			this.publicationYear = this.currentValue.toString().trim();
		} else if (isWithinElement(localName, ELEM_RESOURCE_TYPE, ELEM_RESOURCE)
				&& this.currentValue.length()>0) {
			this.resourceTypeValue = this.currentValue.toString().trim();
		} else if (isWithinElement(localName, ELEM_RESOURCE, ELEM_PAYLOAD) ||
//				temporary hack: the case below is for the records originated from MDStore 
//				where no 'payload' element is present, required until fixing MDStore contents
				isWithinElement(localName, ELEM_RESOURCE, ELEM_METADATA)) {
//			writing whole record
			if (this.idType!=null && this.idValue!=null) {
				try {
					String idValueStr = this.idValue.toString().trim();
					DataSetReference.Builder dataSetRefBuilder = DataSetReference.newBuilder();
					DatasetToMDStore.Builder documentToMDStoreBuilder = DatasetToMDStore.newBuilder();
					documentToMDStoreBuilder.setMdStoreId(this.mdStoreId);
					String datasetId;
					if (this.headerId==null) {
						throw new SAXException("header identifier was not found!");
					}
					if (ELEM_OBJ_IDENTIFIER.equals(mainIdFieldName)) {
						datasetId = HBaseConstants.ROW_PREFIX_RESULT + this.headerId; 
						dataSetRefBuilder.setId(datasetId);	
						documentToMDStoreBuilder.setDatasetId(datasetId);
					} else {
						datasetId = this.headerId;
						dataSetRefBuilder.setId(datasetId);
						documentToMDStoreBuilder.setDatasetId(datasetId);
					}
					dataSetRefBuilder.setReferenceType(this.idType);
					dataSetRefBuilder.setIdForGivenType(idValueStr);
					if (this.creatorNames!=null) {
						dataSetRefBuilder.setCreatorNames(this.creatorNames);
					}
					if (this.titles!=null) {
						dataSetRefBuilder.setTitles(this.titles);
					}
					if (this.formats!=null) {
						dataSetRefBuilder.setFormats(this.formats);
					}
					if (this.description!=null) {
						dataSetRefBuilder.setDescription(this.description);
					}
					if (this.publisher!=null) {
						dataSetRefBuilder.setPublisher(this.publisher);
					}
					if (this.publicationYear!=null) {
						dataSetRefBuilder.setPublicationYear(this.publicationYear);
					}
					if (this.resourceTypeClass!=null) {
						dataSetRefBuilder.setResourceTypeClass(this.resourceTypeClass);
					}
					if (this.resourceTypeValue!=null) {
						dataSetRefBuilder.setResourceTypeValue(this.resourceTypeValue);
					}
					if (this.alternateIdentifiers!=null) {
                        dataSetRefBuilder.setAlternateIdentifiers(this.alternateIdentifiers);
                    }
					datasetReceiver.receive(dataSetRefBuilder.build());
					datasetToMDStoreReceived.receive(documentToMDStoreBuilder.build());
				} catch (IOException e) {
					throw new SAXException(e);
				}
			} else {
				log.warn("either reference type " + this.idType + 
						" or id value: " + this.idValue + 
						" was null for record id: " + this.headerId);
			}
			clearAllFields();
		}
//		resetting current value;
		this.currentValue = null;
	}

	private void clearAllFields() {
		this.headerId = null;
		this.idType = null;
		this.idValue = null;		
		this.creatorNames = null;
		this.titles = null;
		this.formats = null;
		this.alternateIdentifiers = null;
		this.description = null;
		this.publisher = null;
		this.publicationYear = null;
		this.resourceTypeClass = null;
		this.resourceTypeValue = null;
		this.currentAlternateIdentifierType = null;
	}
	
	boolean isWithinElement(String localName,
			String expectedElement, String expectedParent) {
		return localName.equals(expectedElement) && !this.parents.isEmpty() && 
				expectedParent.equals(this.parents.peek());
	}
	
	@Override
	public void endDocument() throws SAXException {
		parents.clear();
		parents = null;
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		if (this.currentValue!=null) {
			this.currentValue.append(ch, start, length);
		}
	}
	
}
