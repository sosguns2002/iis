package eu.dnetlib.iis.common.counter;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Class that simplifies use of pig counters.
 * 
 * @author madryk
 */
public class PigCounters {

    private Map<String, JobCounters> jobLevelCounters = Maps.newHashMap();
    
    private Map<String, String> rootLevelCounters = Maps.newHashMap();
    
    
    
    //------------------------ CONSTRUCTORS --------------------------
    
    /**
     * 
     * @param rootLevelCounters - general pig counters not assigned to a specific job, may not be null 
     * @param jobLevelCounters - pig counters assigned to specific jobs, may not be null
     * 
     * @throws NullPointerException if rootLevelCounters or jobLevelCounters equals to null
     */
    public PigCounters(Map<String, String> rootLevelCounters, List<JobCounters> jobLevelCounters) {
        
        Preconditions.checkNotNull(jobLevelCounters);
        Preconditions.checkNotNull(rootLevelCounters);
        
        this.rootLevelCounters = rootLevelCounters;
        
        for(JobCounters jobCounters : jobLevelCounters) {
            this.jobLevelCounters.put(jobCounters.getJobId(), jobCounters);
        }
        
    }
    
   
    //------------------------ LOGIC --------------------------
    
    /**
     * Returns list of job ids
     */
    public List<String> getJobIds() {
        return Lists.newArrayList(jobLevelCounters.keySet());
    }
    
    /**
     * Returns {@link JobCounters} with provided job id
     * or <code>null</code> if job does not exists.
     */
    public JobCounters getJobCounters(String jobId) {
        return jobLevelCounters.get(jobId);
    }
    
    /**
     * Returns job id with provided alias
     * or <code>null</code> if job does not exists.
     */
    public String getJobIdByAlias(String jobAlias) {
        for (Map.Entry<String, JobCounters> jobCountersEntry : jobLevelCounters.entrySet()) {
            
            if (jobCountersEntry.getValue().getAliases().contains(jobAlias)) {
                return jobCountersEntry.getValue().getJobId();
            }
        }
        return null;
    }
    
    //------------------------ GETTERS --------------------------
    
    /**
     * Returns root level pig counters i.e. general counters not related to any specific job
     */
    public Map<String, String> getRootLevelCounters() {
        return this.rootLevelCounters;
    }
    
    
    //------------------------ INNER CLASSES --------------------------
    
    /**
     * Representation of single map-reduce job counters that were run inside pig job
     */
    public static class JobCounters {
        
        private String jobId;
        private List<String> aliases;
        private Map<String, String> counters;
        
        
        //------------------------ CONSTRUCTORS --------------------------
        
        /**
         * Constructs object with empty counters and no aliases.
         */
        public JobCounters(String jobId) {
            this(jobId, Lists.newArrayList(), Maps.newHashMap());
        }
        
        /**
         * Constructs object with filled counters and aliases.
         */
        public JobCounters(String jobId, List<String> aliases, Map<String, String> counters) {
            this.jobId = jobId;
            this.aliases = aliases;
            this.counters = counters;
        }

        //------------------------ GETTERS --------------------------
        
        /**
         * Returns job id
         */
        public String getJobId() {
            return jobId;
        }

        /**
         * Returns aliases of job
         */
        public List<String> getAliases() {
            return aliases;
        }
        
        
        //------------------------ LOGIC --------------------------
        
        public void addAlias(String alias) {
            aliases.add(alias);
        }

        /**
         * Adds a counter
         */
        public void addCounter(String counterName, String counterValue) {
            counters.put(counterName, counterValue);
        }
        
        /**
         * Returns a number of all counters for the job
         */
        public int getCountersCount() {
            return counters.size();
        }
        
        /**
         * Returns value of a counter with provided name
         */
        public String getCounter(String counterName) {
            return counters.get(counterName);
        }
        
    }
}
