-- !Ups
CREATE VIEW injured_worker_summary AS
SELECT ij.id as worker_id, ij.injury_severity,  wa.date_time, ind.id as industry_id, ind.name as industry_name
FROM injured_workers ij
         left join industries ind on ij.industry_id = ind.id
         inner join work_accidents wa on wa.id = ij.accident_id;

-- !Downs
DROP VIEW injured_worker_summary;
