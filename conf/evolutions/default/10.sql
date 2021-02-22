# -- !Ups

create table work_accidents(
    id serial PRIMARY KEY,
    date_time timestamp,
    entrepreneur_id int,
    region_id int,
    blog_post_url varchar(1024),
    details text,
    investigation text,
    mediaReports text,
    public_remarks text,
    sensitive_remarks text,

    constraint fk_wa_ent foreign key(entrepreneur_id) references business_entities(id) on delete set null,
    constraint fk_wa_rgn foreign key(region_id) references regions(id) on delete set null
);

create table injured_workers(
    id serial PRIMARY KEY,
    accident_id int,
    name varchar(512),
    age int,
    citizenship_id int,
    industry_id int,
    from_place varchar(512),
    injury_cause_id int,
    injury_severity varchar(9),
    injury_description text,
    public_remarks text,
    sensitive_remarks text,

    constraint fk_iw_wa foreign key (accident_id) references work_accidents(id) on delete cascade,
    constraint fk_iw_cz foreign key (citizenship_id) references citizenships(id) on delete set null,
    constraint fk_iw_in foreign key (industry_id) references industries(id) on delete set null,
    constraint fk_iw_ic foreign key (injury_cause_id) references injury_causes(id) on delete set null
);

# -- !Downs

drop table injured_workers;
drop table work_accidents;