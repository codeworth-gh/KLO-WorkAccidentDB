-- !Ups

alter table work_accidents add column requires_update bool;
update work_accidents set requires_update = false;
create index idx_work_accidents_updating ON work_accidents(requires_update);

-- !Downs
drop index idx_work_accidents_updating;
alter table work_accidents drop column requires_update;
