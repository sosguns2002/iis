package eu.dnetlib.iis.wf.affmatching;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import eu.dnetlib.iis.common.java.io.HdfsUtils;
import eu.dnetlib.iis.common.schemas.ReportEntry;
import eu.dnetlib.iis.wf.affmatching.bucket.projectorg.model.AffMatchDocumentProject;
import eu.dnetlib.iis.wf.affmatching.bucket.projectorg.model.AffMatchProjectOrganization;
import eu.dnetlib.iis.wf.affmatching.bucket.projectorg.read.DocumentProjectFetcher;
import eu.dnetlib.iis.wf.affmatching.bucket.projectorg.read.DocumentProjectMerger;
import eu.dnetlib.iis.wf.affmatching.bucket.projectorg.read.IisDocumentProjectReader;
import eu.dnetlib.iis.wf.affmatching.bucket.projectorg.read.IisInferredDocumentProjectReader;
import eu.dnetlib.iis.wf.affmatching.bucket.projectorg.read.IisProjectOrganizationReader;
import eu.dnetlib.iis.wf.affmatching.model.MatchedOrganization;
import eu.dnetlib.iis.wf.affmatching.write.ProjectBasedDocOrgMatchReportGenerator;
import pl.edu.icm.sparkutils.avro.SparkAvroSaver;
import scala.Tuple2;

/**
 * Job matching publications with organizations based on publication->project and project->organization relations.
 * Only projects with single organization will be taken into account. 
 * 
 * 
 * @author mhorst
 */

public class ProjectBasedMatchingJob {
    
	
    //------------------------ LOGIC --------------------------
    
    public static void main(String[] args) throws IOException {
        
    	ProjectRelatedDocOrgMatchingJobParameters params = new ProjectRelatedDocOrgMatchingJobParameters();
        JCommander jcommander = new JCommander(params);
        jcommander.parse(args);
    
        SparkConf conf = new SparkConf();
        conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
        conf.set("spark.kryo.registrator", "pl.edu.icm.sparkutils.avro.AvroCompatibleKryoRegistrator");
        
        SparkAvroSaver sparkAvroSaver = new SparkAvroSaver();
        ProjectBasedDocOrgMatchReportGenerator reportGenerator = new ProjectBasedDocOrgMatchReportGenerator();
        
        final String inputAvroDocProjPath = params.inputAvroDocProjPath;
        final String inputAvroInferredDocProjPath = params.inputAvroInferredDocProjPath;
        final String inputAvroProjOrgPath = params.inputAvroProjOrgPath;
        
        final Float docProjConfidenceThreshold = params.inputDocProjConfidenceThreshold;
        
        final String outputPath = params.outputAvroPath;
        final String outputReportPath = params.outputAvroReportPath;
        
        try (JavaSparkContext sc = new JavaSparkContext(conf)) {
            
            HdfsUtils.remove(sc.hadoopConfiguration(), params.outputAvroPath);
            HdfsUtils.remove(sc.hadoopConfiguration(), params.outputAvroReportPath);

            DocumentProjectFetcher documentProjectFetcher = new DocumentProjectFetcher();
            documentProjectFetcher.setFirstDocumentProjectReader(new IisDocumentProjectReader());
            documentProjectFetcher.setSecondDocumentProjectReader(new IisInferredDocumentProjectReader());
            documentProjectFetcher.setDocumentProjectMerger(new DocumentProjectMerger());
            documentProjectFetcher.setSparkContext(sc);
            documentProjectFetcher.setFirstDocProjPath(inputAvroDocProjPath);
            documentProjectFetcher.setSecondDocProjPath(inputAvroInferredDocProjPath);
            
            IisProjectOrganizationReader projectOrganizationReader = new IisProjectOrganizationReader();
            
            JavaRDD<AffMatchDocumentProject> docProj = documentProjectFetcher.fetchDocumentProjects();
            
            JavaRDD<AffMatchDocumentProject> filteredDocProj = docProjConfidenceThreshold != null
                    ? docProj.filter(e -> e.getConfidenceLevel() >= docProjConfidenceThreshold)
                    : docProj;
            
            JavaRDD<AffMatchProjectOrganization> projOrg = projectOrganizationReader.readProjectOrganizations(sc, inputAvroProjOrgPath);
            
            // accepting project relations only with single organization
            JavaRDD<AffMatchProjectOrganization> filteredProjOrg = projOrg.keyBy(e -> e.getProjectId()).groupByKey()
                    .flatMap(e -> getRelationsWithSingleOrganization(e));
            
            JavaRDD<MatchedOrganization> matchedDocOrg = filteredDocProj.keyBy(e -> e.getProjectId())
                    .join(filteredProjOrg.keyBy(e -> e.getProjectId())).map(e -> buildMatchedOrganization(e));
            
            // deduplicating by grouping by doc+org and picking the highest confidence level
            JavaRDD<MatchedOrganization> distinctMatchedOrganizations = matchedDocOrg.keyBy(e -> buildMatchedOrgKey(e))
                    .reduceByKey((x, y) -> x.getMatchStrength() >= y.getMatchStrength() ? x : y).values();
            
            distinctMatchedOrganizations.cache();
            
            sparkAvroSaver.saveJavaRDD(distinctMatchedOrganizations, MatchedOrganization.SCHEMA$, outputPath);
            
            List<ReportEntry> reportEntries = reportGenerator.generateReport(distinctMatchedOrganizations);
            
            sparkAvroSaver.saveJavaRDD(sc.parallelize(reportEntries), ReportEntry.SCHEMA$, outputReportPath);
            }
    }
    
    
    //------------------------ PRIVATE --------------------------
    
    private static Tuple2<CharSequence, CharSequence> buildMatchedOrgKey(MatchedOrganization element) {
        return new Tuple2<>(element.getDocumentId(), element.getOrganizationId());
    }
    
    private static List<AffMatchProjectOrganization> getRelationsWithSingleOrganization(Tuple2<String, Iterable<AffMatchProjectOrganization>> element) {
        
        Iterator<AffMatchProjectOrganization> it = element._2.iterator();
        if (it.hasNext()) {
            AffMatchProjectOrganization result = it.next();
            if (!it.hasNext()) {
                return Collections.singletonList(result);
            }
        }
        return Collections.emptyList();
    }
    
    private static MatchedOrganization buildMatchedOrganization(Tuple2<String, Tuple2<AffMatchDocumentProject,AffMatchProjectOrganization>> element) {
        MatchedOrganization.Builder builder = MatchedOrganization.newBuilder();
        builder.setDocumentId(element._2._1.getDocumentId());
        builder.setOrganizationId(element._2._2.getOrganizationId());
        //TODO currenlty propagating doc->proj matching confidence level, maybe we should alter this score a little bit 
        builder.setMatchStrength(element._2._1.getConfidenceLevel());
        return builder.build();
    }
    
    @Parameters(separators = "=")
    private static class ProjectRelatedDocOrgMatchingJobParameters {
        
        @Parameter(names = "-inputAvroInferredDocProjPath", required = true, description="path to directory with avro files containing inferred document to project relations")
        private String inputAvroInferredDocProjPath;
        
        @Parameter(names = "-inputAvroDocProjPath", required = true, description="path to directory with avro files containing document to project relations")
        private String inputAvroDocProjPath;
        
        @Parameter(names = "-inputDocProjConfidenceThreshold", required = false, description="minimal confidence level for document to project relations (no limit by default)")
        private Float inputDocProjConfidenceThreshold = null;
        
        @Parameter(names = "-inputAvroProjOrgPath", required = true, description="path to directory with avro files containing project to organization relations")
        private String inputAvroProjOrgPath;
        
        @Parameter(names = "-outputAvroPath", required = true)
        private String outputAvroPath;
        
        @Parameter(names = "-outputAvroReportPath", required = true, description="path to a directory with the execution result report")
        private String outputAvroReportPath;
        
    }
    
}
