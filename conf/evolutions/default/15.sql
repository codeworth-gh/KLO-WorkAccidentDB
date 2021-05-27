-- !Ups
create view bizent_accident as
select distinct business_entity_id, accident_id from bart_accident order by business_entity_id;

create view bizent_accident_stats as
SELECT b.id, b.name, count(wa.id) as accident_count,
       sum(wa.killed_count) as killed_count, sum(wa.injured_count) as injured_count
FROM business_entities b left join bizent_accident ba
                                   on b.id = ba.business_entity_id
                         left join work_accident_summary wa
                                    on wa.id = ba.accident_id
GROUP BY b.id, b.name
HAVING count(wa.id)>0;

-- !Downs
drop view bizent_accident_stats;
drop view bizent_accident;
