alter table data_source
add schema varchar(50);

update data_source
set schema = UPPER(user_name)
WHERE dbms = 'ORACLE'
;
