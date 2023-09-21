-- !Ups
ALTER TABLE work_accidents ADD COLUMN officially_recognized boolean;

CREATE OR REPLACE VIEW work_accident_summary2 AS
SELECT
    wa.id,
    wa.date_time,
    wa.entrepreneur_id,
    wa.region_id,
    wa.location,
    wa.details,
    wa.investigation,
    wa.requires_update,
    (SELECT count(*) FROM injured_workers iw WHERE iw.accident_id=wa.id ) injured_count,
    (SELECT count(*) FROM injured_workers iw WHERE iw.accident_id=wa.id AND iw.injury_severity=4 ) killed_count,
    wa.officially_recognized
FROM work_accidents wa;


-- !Downs

ALTER TABLE work_accidents DROP COLUMN officially_recognized;

