# -- !Ups
create table injury_causes (
    id serial PRIMARY KEY,
    name varchar(64) UNIQUE
);

# -- !Downs
drop table injury_causes;