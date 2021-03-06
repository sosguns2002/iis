package eu.dnetlib.iis.wf.importer.infospace.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import eu.dnetlib.data.proto.FieldTypeProtos.Author;
import eu.dnetlib.data.proto.FieldTypeProtos.StructuredProperty;
import eu.dnetlib.data.proto.OafProtos.OafEntity;
import eu.dnetlib.data.proto.ResultProtos.Result;
import eu.dnetlib.data.proto.ResultProtos.Result.Metadata;
import eu.dnetlib.data.proto.TypeProtos.Type;
import eu.dnetlib.iis.common.InfoSpaceConstants;
import eu.dnetlib.iis.importer.schemas.DocumentMetadata;
import eu.dnetlib.iis.wf.importer.infospace.approver.FieldApprover;

/**
 * {@link DocumentMetadataConverter} test class.
 */
public class DocumentMetadataConverterTest {

    private static final String ID = "document id";
    private static final String TITLE = "Document Title";
    private static final String OTHER_TITLE = "Other "+TITLE;
    private static final String ABSTRACT = "Document abstract";
    private static final String LANGUAGE = "polish";
    private static final ImmutableList<String> KEYWORDS = ImmutableList.of("keyword 1", "keyword 2");
    private static final ImmutableMap<String, String> EXT_IDENTIFIERS = ImmutableMap.of("k1", "v1", "k2", "v2");
    private static final String JOURNAL = "Journal Title";
    private static final Integer YEAR = 2000;
    private static final String PUBLISHER = "Publisher Name";
    private static final ImmutableList<String> DATASOURCE_IDS = ImmutableList.of("source id 1", "id #2");

    private static final String FIRST_NAME = "a first name";
    private static final String SECOND_NAME = "a second name";
    private static final String SECOND_SECOND_NAME = "another name";
    private static final String FULL_NAME = "the full name";
    
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @InjectMocks
    private DocumentMetadataConverter converter;

    @Mock
    private FieldApprover fieldApprover;

    @Before
    public void setUp() {
        when(fieldApprover.approve(any())).thenReturn(true);
    }

    // ------------------------ TESTS --------------------------

    @Test(expected=NullPointerException.class)
    public void convert_null_oafEntity() throws IOException {
        // execute
        converter.convert(null);
    }

    @Test
    public void convert_using_main_title() throws IOException {
        // given
        OafEntity.Builder builder = minimalEntityBuilder(ID, InfoSpaceConstants.SEMANTIC_CLASS_INSTANCE_TYPE_ARTICLE);

        addTitle(builder, OTHER_TITLE);
        addTitle(builder, TITLE).getQualifierBuilder().setClassid(InfoSpaceConstants.SEMANTIC_CLASS_MAIN_TITLE);

        OafEntity oafEntity = builder.build();

        // execute
        DocumentMetadata metadata = converter.convert(oafEntity);

        // assert
        assertEquals(TITLE, metadata.getTitle());
    }

    @Test
    public void convert_using_first_title() throws IOException {
        // given
        OafEntity.Builder builder = minimalEntityBuilder(ID, InfoSpaceConstants.SEMANTIC_CLASS_INSTANCE_TYPE_ARTICLE);

        addTitle(builder, OTHER_TITLE);
        addTitle(builder, TITLE);

        OafEntity oafEntity = builder.build();

        // execute
        DocumentMetadata metadata = converter.convert(oafEntity);

        // assert
        assertEquals(OTHER_TITLE, metadata.getTitle());
    }

    @Test
    public void convert_skip_null_abstract() throws IOException {
        // given
        OafEntity.Builder builder = minimalEntityBuilder(ID, InfoSpaceConstants.SEMANTIC_CLASS_INSTANCE_TYPE_ARTICLE);

        addDescription(builder, "null");
        addDescription(builder, ABSTRACT);

        OafEntity oafEntity = builder.build();

        // execute
        DocumentMetadata metadata = converter.convert(oafEntity);

        // assert
        assertEquals(ABSTRACT, metadata.getAbstract$());
    }

    @Test
    public void convert_with_undefined_language() throws IOException {
        // given
        OafEntity.Builder builder = minimalEntityBuilder(ID, InfoSpaceConstants.SEMANTIC_CLASS_INSTANCE_TYPE_ARTICLE);

        setLanguage(builder, "und");

        OafEntity oafEntity = builder.build();

        // execute
        DocumentMetadata metadata = converter.convert(oafEntity);

        // assert
        assertEquals(null, metadata.getLanguage());
    }


    @Test
    public void convert_not_approved() throws IOException {
        // given
        OafEntity oafEntity = documentEntity();

        when(fieldApprover.approve(any())).thenReturn(false);

        // execute
        DocumentMetadata metadata = converter.convert(oafEntity);

        // assert
        assertEquals(ID, metadata.getId());
        assertEquals(null, metadata.getTitle());
        assertEquals(null, metadata.getAbstract$());
        assertEquals(LANGUAGE, metadata.getLanguage());
        assertEquals(null, metadata.getKeywords());
        assertEquals(null, metadata.getExternalIdentifiers());
        assertEquals(null, metadata.getJournal());
        assertEquals(null, metadata.getYear());
        assertEquals(null, metadata.getPublisher());
        assertTrue(metadata.getPublicationType().getArticle());
        assertTrue(metadata.getPublicationType().getDataset());
        assertEquals(DATASOURCE_IDS, metadata.getDatasourceIds());
        
        assertEquals(1, metadata.getAuthors().size());
        assertEquals(FIRST_NAME, metadata.getAuthors().get(0).getName());
        assertEquals(SECOND_NAME + ' ' + SECOND_SECOND_NAME, metadata.getAuthors().get(0).getSurname());
        assertEquals(FULL_NAME, metadata.getAuthors().get(0).getFullname());
    }

    @Test
    public void convert() throws IOException {
        // given
        OafEntity oafEntity = documentEntity();

        // execute
        DocumentMetadata metadata = converter.convert(oafEntity);

        // assert
        assertEquals(ID, metadata.getId());
        assertEquals(TITLE, metadata.getTitle());
        assertEquals(ABSTRACT, metadata.getAbstract$());
        assertEquals(LANGUAGE, metadata.getLanguage());
        assertEquals(KEYWORDS, metadata.getKeywords());
        assertEquals(EXT_IDENTIFIERS, metadata.getExternalIdentifiers());
        assertEquals(JOURNAL, metadata.getJournal());
        assertEquals(YEAR, metadata.getYear());
        assertEquals(PUBLISHER, metadata.getPublisher());
        assertTrue(metadata.getPublicationType().getArticle());
        assertTrue(metadata.getPublicationType().getDataset());
        
        assertEquals(1, metadata.getAuthors().size());
        assertEquals(FIRST_NAME, metadata.getAuthors().get(0).getName());
        assertEquals(SECOND_NAME + ' ' + SECOND_SECOND_NAME, metadata.getAuthors().get(0).getSurname());
        assertEquals(FULL_NAME, metadata.getAuthors().get(0).getFullname());
        
        assertEquals(DATASOURCE_IDS, metadata.getDatasourceIds());
    }

    // ------------------------ PRIVATE --------------------------

    private static OafEntity.Builder emptyEntityBuilder(String id) {
        // note that the type does not matter for the converter
        return OafEntity.newBuilder().setType(Type.result).setId(id);
    }

    private static OafEntity.Builder minimalEntityBuilder(String id, String... types) {
        OafEntity.Builder builder = emptyEntityBuilder(id);
        addPublicationTypes(builder.getResultBuilder(), types);
        return builder;
    }

    private static OafEntity documentEntity() {
        OafEntity.Builder oafBuilder = minimalEntityBuilder(ID,
                InfoSpaceConstants.SEMANTIC_CLASS_INSTANCE_TYPE_ARTICLE,
                InfoSpaceConstants.SEMANTIC_CLASS_INSTANCE_TYPE_DATASET);

        Metadata.Builder mdBuilder = oafBuilder.getResultBuilder().getMetadataBuilder();

        addTitle(oafBuilder, TITLE);
        addDescription(oafBuilder, ABSTRACT);
        setLanguage(oafBuilder, LANGUAGE);

        for (String keyword : KEYWORDS) {
            mdBuilder.addSubjectBuilder().setValue(keyword);
        }

        for (Entry<String, String> entry : EXT_IDENTIFIERS.entrySet()) {
            oafBuilder.addPidBuilder().setValue(entry.getValue()).getQualifierBuilder().setClassid(entry.getKey());
        }

        mdBuilder.getJournalBuilder().setName(JOURNAL);
        mdBuilder.getDateofacceptanceBuilder().setValue(String.format("%s-02-29", YEAR));
        mdBuilder.getPublisherBuilder().setValue(PUBLISHER);


        for (String id : DATASOURCE_IDS) {
            oafBuilder.addCollectedfromBuilder().setKey(id);
        }

        
        Author.Builder authorBuilder = Author.newBuilder();
        authorBuilder.setName(FIRST_NAME);
        authorBuilder.setSurname(SECOND_NAME + ' ' + SECOND_SECOND_NAME);
        authorBuilder.setFullname(FULL_NAME);
        authorBuilder.setRank(0);
        oafBuilder.getResultBuilder().getMetadataBuilder().addAuthor(authorBuilder);
        
        return oafBuilder.build();
    }

    private static void addPublicationTypes(Result.Builder resBuilder, String... types) {
        for (String type : types) {
            resBuilder.addInstanceBuilder().getInstancetypeBuilder()
                .setClassid(type);
        }
    }

    private static StructuredProperty.Builder addTitle(OafEntity.Builder builder, String value) {
        return builder.getResultBuilder().getMetadataBuilder().addTitleBuilder().setValue(value);
    }

    private static void addDescription(OafEntity.Builder builder, String value) {
        builder.getResultBuilder().getMetadataBuilder().addDescriptionBuilder().setValue(value);
    }

    private static void setLanguage(OafEntity.Builder builder, String value) {
        builder.getResultBuilder().getMetadataBuilder().getLanguageBuilder().setClassid(value);
    }

}
