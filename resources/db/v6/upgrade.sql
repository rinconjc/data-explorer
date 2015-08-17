alter table data_source
alter password varchar(255)
;
create table ds_table(id int not null
, name varchar(100) not null
, alias varchar(100)
, type varchar(20)
, data_source_id int
, primary key (id)
, foreign key (data_source_id) references data_source(id)
, unique (data_source_id, name) )
;

create table ds_column(id int not null
, table_id int
, name varchar(100)
, alias varchar(100)
, data_type int
, type_name varchar(50)
, size int
, digits int
, nullable bit
, is_pk bit
, is_fk bit
, fk_table varchar(100)
, fk_column varchar(100)
, primary key (id)
, unique(table_id, name)
, foreign key(table_id) references ds_table(id))
