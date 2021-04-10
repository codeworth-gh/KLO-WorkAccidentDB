-- !Ups

insert into bizent_accident_relation_type(name) VALUES('יזם');

insert into bart_accident (accident_id, bart_id, business_entity_id)
select id, (select id from bizent_accident_relation_type where name='יזם'), entrepreneur_id from work_accidents
where entrepreneur_id is not null;


-- !Downs

delete from bart_accident;


