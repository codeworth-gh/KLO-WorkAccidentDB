-- Create merge log entries

-- !Ups
create table entity_merge_log_entry(
    merge_id uuid,
    table_name varchar,
    message varchar
);


-- !Downs
drop table entity_merge_log_entry;