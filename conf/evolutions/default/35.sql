-- !Ups
CREATE VIEW injured_worker_summary AS
SELECT ij.id as worker_id, ij.injury_severity,  wa.date_time, ind.id as industry_id, ind.name as industry_name,
       ct.id as citizenship_id, ct.name as citizenship_name,
       ic.id as injury_cause_id, ic.name as injury_cause_name
FROM injured_workers ij
         left join industries ind on ij.industry_id = ind.id
         left join citizenships ct on ij.citizenship_id = ct.id
         left join injury_causes ic on ij.injury_cause_id = ic.id
         inner join work_accidents wa on wa.id = ij.accident_id;

-- !Downs
DROP VIEW injured_worker_summary;
