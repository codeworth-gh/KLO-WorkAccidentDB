# -- !Ups
create table regions (
    id serial PRIMARY KEY,
    name varchar(64) UNIQUE
);

# -- !Downs
drop table regions;