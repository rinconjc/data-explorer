truncate table data_source;

alter table data_source
alter column name varchar(50) not null;

alter table data_source
alter column dbms varchar(50) not null;

alter table data_source
alter column url varchar(250) not null;

