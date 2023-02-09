-- Adding sanctions

-- !Ups
CREATE TABLE sanctions (
       id serial PRIMARY KEY ,
       business_entity_id int,
       authority varchar(1024),
       sanction_type varchar(1024),
       reason text,
       application_date date,
       remarks text,

       constraint fk_wa_ent foreign key(business_entity_id) references business_entities(id) on delete cascade
);
CREATE INDEX sanctions_by_be on sanctions(business_entity_id);

-- !Downs
DROP INDEX sanctions_by_be;
DROP TABLE sanctions;
