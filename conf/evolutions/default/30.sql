-- add h.p. number to business entities
-- !Ups
ALTER TABLE business_entities ADD COLUMN pc_number integer;
CREATE INDEX business_entity_pc_num on business_entities(pc_number);

-- !Downs
DROP INDEX business_entity_pc_num;
ALTER TABLE business_entities DROP COLUMN pc_number;
