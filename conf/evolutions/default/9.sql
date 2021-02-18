# -- !Ups
create table business_entities (
    id serial PRIMARY KEY,
    name varchar(128),
    phone varchar(64),
    email varchar(64),
    website varchar(128),
    is_private_person bool,
    memo text
);

create index business_entities_name ON business_entities(name);

# -- !Downs
drop index business_entities_name;
drop table business_entities;