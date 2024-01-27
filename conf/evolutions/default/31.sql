-- Add safety_violation_sanctions

-- !Ups
CREATE TABLE safety_violations_sanctions(
    id serial primary key,
    gov_id int,
    sanction_number int,
    sanction_date date,
    company_name varchar,
    pc_number int,
    violation_site varchar,
    violation_clause varchar,
    sum int,
    commissioners_decision varchar,
    klo_business_entity_id int
);

CREATE INDEX svs_by_biz_ent on safety_violations_sanctions(klo_business_entity_id);
CREATE INDEX svs_by_gov_id on safety_violations_sanctions(gov_id);

-- !Downs
DROP INDEX svs_by_gov_id;
DROP INDEX svs_by_biz_ent;
DROP TABLE safety_violations_sanctions;
