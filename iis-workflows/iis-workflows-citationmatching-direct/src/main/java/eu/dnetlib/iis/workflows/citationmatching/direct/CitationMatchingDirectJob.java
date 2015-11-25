package eu.dnetlib.iis.workflows.citationmatching.direct;

import java.io.IOException;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import eu.dnetlib.iis.citationmatching.direct.schemas.Citation;
import eu.dnetlib.iis.citationmatching.direct.schemas.DocumentMetadata;
import eu.dnetlib.iis.common.spark.avro.SparkAvroLoader;
import eu.dnetlib.iis.common.spark.avro.SparkAvroSaver;
import eu.dnetlib.iis.transformers.metadatamerger.schemas.ExtractedDocumentMetadataMergedWithOriginal;
import eu.dnetlib.iis.workflows.citationmatching.direct.converter.DirectCitationToCitationConverter;
import eu.dnetlib.iis.workflows.citationmatching.direct.converter.DocumentToDirectCitationMetadataConverter;
import eu.dnetlib.iis.workflows.citationmatching.direct.model.IdWithPosition;
import eu.dnetlib.iis.workflows.citationmatching.direct.service.ExternalIdCitationMatcher;
import eu.dnetlib.iis.workflows.citationmatching.direct.service.PickResearchArticleDocumentFunction;
import eu.dnetlib.iis.workflows.citationmatching.direct.service.PickFirstDocumentFunction;


public class CitationMatchingDirectJob {
    
    
    private static DocumentToDirectCitationMetadataConverter documentToDirectCitationMetadataConverter = new DocumentToDirectCitationMetadataConverter();
    
    private static ExternalIdCitationMatcher externalIdCitationMatcher = new ExternalIdCitationMatcher();
    
    private static DirectCitationToCitationConverter directCitationToCitationConverter = new DirectCitationToCitationConverter();
    
    
    
    //------------------------ LOGIC --------------------------
    
    public static void main(String[] args) throws IOException {
        
        CitationMatchingDirectJobParameters params = new CitationMatchingDirectJobParameters();
        JCommander jcommander = new JCommander(params);
        jcommander.parse(args);
        
        
        SparkConf conf = new SparkConf();
        conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
        conf.set("spark.kryo.registrator", "eu.dnetlib.iis.core.spark.AvroCompatibleKryoRegistrator");
        
        
        
        try (JavaSparkContext sc = new JavaSparkContext(conf)) {
            
            JavaRDD<ExtractedDocumentMetadataMergedWithOriginal> documents = SparkAvroLoader.loadJavaRDD(sc, params.inputAvroPath, ExtractedDocumentMetadataMergedWithOriginal.class);
            
            
            JavaRDD<DocumentMetadata> simplifiedDocuments = documents.map(document -> documentToDirectCitationMetadataConverter.convert(document));
//            simplifiedDocumentsMetadata = simplifiedDocumentsMetadata.cache(); // FIXME: https://github.com/openaire/iis/issues/128
            
            
            JavaRDD<Citation> directDoiCitations = externalIdCitationMatcher.matchCitations(simplifiedDocuments, "doi", new PickFirstDocumentFunction());
            
            JavaRDD<Citation> directPmidCitations = externalIdCitationMatcher.matchCitations(simplifiedDocuments, "pmid", new PickResearchArticleDocumentFunction());
            
            JavaRDD<Citation> directCitations = mergeCitations(directDoiCitations, directPmidCitations);
            
            
            
            JavaRDD<eu.dnetlib.iis.common.citations.schemas.Citation> citations = 
                    directCitations.map(directCitation -> directCitationToCitationConverter.convert(directCitation));
            
            
            SparkAvroSaver.saveJavaRDD(citations, eu.dnetlib.iis.common.citations.schemas.Citation.SCHEMA$, params.outputAvroPath);
        }
        
    }
    
    
    //------------------------ PRIVATE --------------------------
    
    
    private static JavaRDD<Citation> mergeCitations(JavaRDD<Citation> directDoiCitations, JavaRDD<Citation> directPmidCitations) {
        
        JavaPairRDD<IdWithPosition, Citation> directDoiCitationsWithKey = attachIdWithPositionKey(directDoiCitations);
        JavaPairRDD<IdWithPosition, Citation> directPmidCitationsWithKey = attachIdWithPositionKey(directPmidCitations);
        
        JavaRDD<Citation> directCitations = directDoiCitationsWithKey.fullOuterJoin(directPmidCitationsWithKey)
                .map(x -> x._2._1.isPresent() ? x._2._1.get() : x._2._2.get() );
        
        return directCitations;
    }
    
    private static JavaPairRDD<IdWithPosition, Citation> attachIdWithPositionKey(JavaRDD<Citation> directCitations) {
        JavaPairRDD<IdWithPosition, Citation> directCitationsWithKey = directCitations
                .keyBy(directCitation -> new IdWithPosition(directCitation.getSourceDocumentId().toString(), directCitation.getPosition()));
        
        return directCitationsWithKey;
    }
    
    
    @Parameters(separators = "=")
    private static class CitationMatchingDirectJobParameters {
        
        @Parameter(names = "-inputAvroPath", required = true)
        private String inputAvroPath;
        
        @Parameter(names = "-outputAvroPath", required = true)
        private String outputAvroPath;
        
        
    }
}