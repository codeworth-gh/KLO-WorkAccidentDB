-- !Ups

create table safety_warrants (
    id               int primary key,
    sent_date        date,
    operator_text_id varchar(256),
    operator_name    varchar(256),
    city_name        varchar(512),
    executor_name    varchar(256),
    category_name    varchar(256),
    felony           varchar(1024),
    law              varchar(1024),
    clause           varchar(1024),
    scrape_date      timestamp,

    klo_operator_id   int,
    klo_executor_id   int,
    klo_industry_id   int,

    constraint fk_operator_id foreign key(klo_operator_id) references business_entities(id) on delete cascade,
    constraint fk_executor_id foreign key(klo_executor_id) references business_entities(id) on delete cascade,
    constraint fk_industry_id foreign key(klo_industry_id) references industries(id) on delete cascade
);

create table business_entity_mapping(
    id serial primary key,
    name varchar,
    biz_entity_id int,

    constraint fk_be_id foreign key (biz_entity_id) references business_entities(id)
);

create table industry_mapping(
    id serial primary key,
    name varchar,
    industry_id int,

    constraint fk_be_id foreign key (industry_id) references industries(id)
);

create index idx_sw_by_comp ON safety_warrants(klo_operator_id);
create index idx_sw_by_exec ON safety_warrants(klo_executor_id);
create index idx_sw_by_inry ON safety_warrants(klo_industry_id);

create index bem_by_str ON business_entity_mapping(name);
create index im_by_str ON industry_mapping(name);

-- !Downs

drop index idx_sw_by_comp;
drop index idx_sw_by_exec;
drop index idx_sw_by_inry;
drop index bem_by_str;
drop index im_by_str;

drop table safety_warrants;
drop table business_entity_mapping;
drop table industry_mapping;
