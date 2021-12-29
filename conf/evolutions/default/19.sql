-- !Ups
create table settings(
    name varchar primary key,
    value varchar
);

-- !Downs

drop table settings;
