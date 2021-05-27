-- !Ups

insert into bizent_accident_relation_type(name, id) VALUES('מקום עבודה', 1024);

insert into bart_accident (accident_id, bart_id, business_entity_id)
    select distinct accident_id, 1024, employer_id
    from injured_workers
    where employer_id is not null;



-- !Downs
delete from bart_accident where bart_id=1024;
delete from bizent_accident_relation_type where id=1024;
