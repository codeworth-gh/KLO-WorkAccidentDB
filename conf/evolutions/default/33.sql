-- !Ups
alter table safety_warrants
add column source text;

update safety_warrants set source='scraper (old)' where true;

-- !Downs
alter table safety_warrants
    drop column source;
