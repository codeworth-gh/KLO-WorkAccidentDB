# -- !Ups
create table citizenships (
    id serial PRIMARY KEY,
    name varchar(64) UNIQUE
);

# -- !Downs
drop table citizenships;