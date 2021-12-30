-- Safety warrant materialized views
-- !Ups
-- warrants per executor per year per
CREATE MATERIALIZED VIEW safety_warrants_per_executor_per_year AS
    SELECT executor_name, date_part('year', sent_date) as year, count(*) as count
    from safety_warrants
    group by executor_name, date_part('year', sent_date)
    order by executor_name;

-- warrants per executor
CREATE MATERIALIZED VIEW safety_warrants_per_executor AS
    select executor_name, sum(count) as count from safety_warrants_per_executor_per_year
    group by executor_name;

-- top 20 exeutors of all time
CREATE MATERIALIZED VIEW safety_warrants_top_20_executors AS
    WITH top_20_cnts AS (
    select distinct count
        from safety_warrants_per_executor
        where executor_name <> ''
        order by count desc
        limit 20
    ) select executor_name, count
        from safety_warrants_per_executor
        where count >= (select min(count) from top_20_cnts)
        order by count desc
    ;

CREATE MATERIALIZED VIEW safety_warrant_over_10_after_2018 AS
    select executor_name, sum(count) as count
    from safety_warrants_per_executor_per_year
    where year >= 2018
    group by executor_name
    having sum(count) >= 10
    order by sum(count) desc;

-- !Downs
DROP MATERIALIZED VIEW safety_warrant_over_10_after_2018;
DROP MATERIALIZED VIEW safety_warrants_top_20_executors;
DROP MATERIALIZED VIEW safety_warrants_per_executor;
DROP MATERIALIZED VIEW safety_warrants_per_executor_per_year;