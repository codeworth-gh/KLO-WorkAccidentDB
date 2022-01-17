-- !Ups
select * into safety_warrants_raw from safety_warrants;

-- !Downs
drop table safety_warrants_raw;
