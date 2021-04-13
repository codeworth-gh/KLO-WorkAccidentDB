# -- !Ups
create table bizent_accident_relation_type (
     id serial PRIMARY KEY,
     name varchar(64) UNIQUE
);

create table bart_accident (
    accident_id int,
    bart_id int,
    business_entity_id int,

    foreign key (accident_id) REFERENCES work_accidents(id) on delete cascade,
    foreign key (business_entity_id) REFERENCES business_entities(id) on delete cascade,
    foreign key (bart_id) references bizent_accident_relation_type(id) on delete cascade,
    primary key (accident_id, bart_id, business_entity_id)
);

create index bart_accident_by_acc ON bart_accident(accident_id);
create index bart_accident_by_biz ON bart_accident(business_entity_id);

# -- !Downs
drop index bart_accident_by_acc;
drop index bart_accident_by_biz;
drop table bart_accident;

drop table bizent_accident_relation_type;