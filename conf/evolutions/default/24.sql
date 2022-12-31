-- !Ups
ALTER TABLE business_entities ADD COLUMN is_known_contractor boolean;
UPDATE business_entities set is_known_contractor=false where true;


-- !Downs
ALTER TABLE business_entities DROP COLUMN is_known_contractor;
