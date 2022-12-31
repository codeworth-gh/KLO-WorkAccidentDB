-- !Ups
DROP VIEW bizent_accident_stats;
CREATE OR REPLACE VIEW bizent_accident_stats AS
SELECT b.id, b.name, b.is_known_contractor, count(wa.id) as accident_count,
       sum(wa.killed_count) as killed_count, sum(wa.injured_count) as injured_count
FROM business_entities b left join bizent_accident ba
                                   on b.id = ba.business_entity_id
                         left join work_accident_summary wa
                                   on wa.id = ba.accident_id
GROUP BY b.id, b.name, b.is_known_contractor
HAVING count(wa.id)>0;

-- !Downs
