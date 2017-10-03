define avro_load_concept AvroStorage('$schema');
define avro_load_concept_blacklist AvroStorage('$schema');
define avro_store_concept AvroStorage('$schema', '-doublecolons');

concept = load '$input' using avro_load_concept;
concept_blacklist = load '$input_blacklist' using avro_load_concept_blacklist;

joined = join concept by (documentId, conceptId) LEFT OUTER, concept_blacklist by (documentId, conceptId);
filtered = filter joined by concept_blacklist::documentId is null and concept_blacklist::conceptId is null;

store filtered into '$output' using avro_store_concept;
