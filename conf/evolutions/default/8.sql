# -- !Ups
-- Insert default data.

insert into citizenships (name) VALUES ('ישראלי-יהודי');
insert into citizenships (name) VALUES ('ישראלי-ערבי');
insert into citizenships (name) VALUES ('פלסטיני');
insert into citizenships (name) VALUES ('תאילנדי');
insert into citizenships (name) VALUES ('אחר');
insert into citizenships (name) VALUES ('סיני');

insert into industries (name) VALUES ('תעשיה ושירותים');
insert into industries (name) VALUES ('בניין');
insert into industries (name) VALUES ('חקלאות');
insert into industries (name) VALUES ('אחר');

insert into injury_causes (name) VALUES ('נפילה מגובה');
insert into injury_causes (name) VALUES ('נפילת חפץ');
insert into injury_causes (name) VALUES ('דריסה');
insert into injury_causes (name) VALUES ('התחשמלות');
insert into injury_causes (name) VALUES ('קריסה');
insert into injury_causes (name) VALUES ('מכשיר עבודה');
insert into injury_causes (name) VALUES ('לכידה במכונה');
insert into injury_causes (name) VALUES ('מלגזה');
insert into injury_causes (name) VALUES ('רכב עבודה');
insert into injury_causes (name) VALUES ('אחר');

insert into regions (name) VALUES ('דרום');
insert into regions (name) VALUES ('מרכז');
insert into regions (name) VALUES ('הצפון וחיפה');
insert into regions (name) VALUES ('גדה - שטחים');

# -- !Downs
delete from citizenships;
delete from industries;
delete from injury_causes;
delete from regions;