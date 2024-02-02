-- !Ups
CREATE OR REPLACE VIEW bizent_accident_stats AS
WITH biz_data AS (SELECT b.id, b.name, b.is_known_contractor, count(svs.id) as safety_violation_sanction_count
                  FROM business_entities b
                           left join safety_violations_sanctions svs on svs.klo_business_entity_id = b.id
                  group by b.id, b.name, b.is_known_contractor)
SELECT b.id, b.name, b.is_known_contractor, count(wa.id) as accident_count,
       coalesce( sum(wa.killed_count), 0) as killed_count, coalesce( sum(wa.injured_count),0) as injured_count,
       b.safety_violation_sanction_count
FROM biz_data b
         left join public.safety_violations_sanctions svs on b.id = svs.klo_business_entity_id
         left join bizent_accident ba on b.id = ba.business_entity_id
         left join work_accident_summary wa on wa.id = ba.accident_id
GROUP BY b.id, b.name, b.is_known_contractor, b.safety_violation_sanction_count;

-- !Downs
DROP VIEW bizent_accident_stats;