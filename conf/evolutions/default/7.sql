# -- !Ups
create table industries (
    id serial PRIMARY KEY,
    name varchar(64) UNIQUE
);

# -- !Downs
drop table industries;