# -- !Ups

CREATE VIEW work_accident_summary AS
    SELECT
        wa.id,
        wa.date_time,
        wa.entrepreneur_id,
        be.name entrepreneur_name,
        wa.region_id,
        wa.details,
        wa.investigation,
        (SELECT count(*) FROM injured_workers iw WHERE iw.accident_id=wa.id ) injured_count,
        (SELECT count(*) FROM injured_workers iw WHERE iw.accident_id=wa.id AND iw.injury_severity=4 ) killed_count
    FROM work_accidents wa
        LEFT JOIN business_entities be
        ON wa.entrepreneur_id=be.id;

# -- !Downs

drop view work_accident_summary;

