-- !Ups
alter table safety_warrants
    add column source text;
update safety_warrants set source='scraper (old)' where true;

alter table safety_warrants_raw
    add column source text;
update safety_warrants_raw set source='scraper (old)' where true;


-- !Downs
alter table safety_warrants_raw drop column source;
alter table safety_warrants drop column source;
