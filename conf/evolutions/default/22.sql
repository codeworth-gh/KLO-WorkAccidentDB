-- !Ups
CREATE MATERIALIZED VIEW safety_warrant_by_category_all AS
SELECT category_name as name, count(*) as count
FROM safety_warrants
GROUP BY category_name
ORDER BY count desc;

CREATE MATERIALIZED VIEW safety_warrant_by_category_24mo AS
SELECT category_name as name, count(*) as count
FROM safety_warrants
WHERE sent_date >= (now() - interval '24 months')
GROUP BY category_name
ORDER BY count desc;

CREATE MATERIALIZED VIEW executors_with_4_plus_24mo AS
SELECT executor_name as name, count(*) as count
FROM safety_warrants
WHERE sent_date >= (now() - interval '24 months')
GROUP BY executor_name
HAVING count(*) > 4
ORDER BY count desc;

CREATE MATERIALIZED VIEW safety_warrant_by_category_and_year AS
SELECT category_name as name, date_part('year', sent_date) as year, count(*)
FROM safety_warrants
GROUP BY category_name, date_part('year',sent_date);

CREATE MATERIALIZED VIEW safety_warrant_by_law AS
SELECT law as name, count(*) as count
FROM safety_warrants
WHERE law <> ''
GROUP BY law
order by count desc;

-- !Downs
drop materialized view safety_warrant_by_law;
drop materialized view safety_warrant_by_category_and_year;
drop materialized view executors_with_4_plus_24mo;
drop materialized view safety_warrant_by_category_24mo;
drop materialized view safety_warrant_by_category_all;
